package com.shtf.zfr.utils.hkismei.callBack;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.shtf.zfr.bean.entity.FaceDb;
import com.shtf.zfr.bean.entity.FaceDevice;
import com.shtf.zfr.bean.entity.FaceRecord;
import com.shtf.zfr.mapper.FaceDbMapper;
import com.shtf.zfr.mapper.FaceDeviceMapper;
import com.shtf.zfr.mapper.FaceRecordMapper;
import com.shtf.zfr.utils.hkismei.HCNetSDK;
import com.shtf.zfr.utils.hkismei.HIKDevice;
import com.shtf.zfr.utils.hkismei.HIKHelper;
import com.shtf.zfr.utils.mybatis.UUIDHelper;
import com.shtf.zfr.utils.spring.SpringContextHelper;
import com.sun.jna.Pointer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import sun.dc.pr.PRError;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
public class FACE_SHOT_NET_DVR_SetDVRMessageCallBack_V50 implements HCNetSDK.FMSGCallBack_V31 {

    @Override
    public boolean invoke(int lCommand, HCNetSDK.NET_DVR_ALARMER pAlarmer, Pointer pAlarmInfo, int dwBufLen, Pointer pUser) {
//        log.info("[HKSDK]收到设备布防报警,报警事件类型lCommand：{}:", Integer.toHexString(lCommand));
        switch (lCommand) {
            case HCNetSDK.COMM_UPLOAD_FACESNAP_RESULT:
//                目前所有的人脸处理都处理人脸比对接口
//                faceCapture(pAlarmInfo);
                break;
            case HCNetSDK.COMM_SNAP_MATCH_ALARM:
                faceMatch(pAlarmer, pAlarmInfo);
                break;
            default:
                log.info("[HKSDK]其他不处理的报警类型:{}", lCommand);
                break;
        }
        return true;
    }

    private void faceMatch(HCNetSDK.NET_DVR_ALARMER pAlarmer, Pointer pAlarmInfo) {
        String recordMemo="";
        HIKHelper hikHelper = SpringContextHelper.getBean(HIKHelper.class);
        FaceDbMapper faceDbMapper = SpringContextHelper.getBean(FaceDbMapper.class);
        FaceRecordMapper faceRecordMapper = SpringContextHelper.getBean(FaceRecordMapper.class);
        String faceRecordUuid = UUIDHelper.getUuid();
        log.info("[HKSDK][人脸比对][{}]收到消息", faceRecordUuid);
        //读取上报数据
        HCNetSDK.NET_VCA_FACESNAP_MATCH_ALARM strFaceSnapMatch = new HCNetSDK.NET_VCA_FACESNAP_MATCH_ALARM();
        strFaceSnapMatch.write();
        Pointer pFaceSnapMatch = strFaceSnapMatch.getPointer();
        pFaceSnapMatch.write(0, pAlarmInfo.getByteArray(0, strFaceSnapMatch.size()), 0, strFaceSnapMatch.size());
        strFaceSnapMatch.read();

        //读取faceDBId和facedbPicId
        String faceDbId = HIKHelper.getValFromPointerStr(strFaceSnapMatch.struBlockListInfo.pFDID, strFaceSnapMatch.struBlockListInfo.dwFDIDLen), faceDbPicId = HIKHelper.getValFromPointerStr(strFaceSnapMatch.struBlockListInfo.pPID, strFaceSnapMatch.struBlockListInfo.dwPIDLen);
        //获取内存中对应的HIKDevice实体
        HIKDevice hikDevice = hikHelper.getDeviceByLoginId(pAlarmer.lUserID);
        if (hikDevice == null) {
            log.error("[HKSDK][人脸比对失败][{}]未获取到登陆的设备，消息内容，人脸库ID：{}，比对结果：{}，相似度：{}", faceRecordUuid, HIKHelper.getDeviceDbSign(faceDbId, faceDbPicId), strFaceSnapMatch.byContrastStatus, strFaceSnapMatch.fSimilarity);
            return;
        }
        //输出比对日志，比对结果，0-保留，1-比对成功，2-比对失败
        log.info("[HKSDK][人脸比对][{}]相机：{}，消息内容，人脸库ID：{}，比对结果：{}，相似度：{}", faceRecordUuid, hikDevice.getDevice().getName(), HIKHelper.getDeviceDbSign(faceDbId, faceDbPicId), strFaceSnapMatch.byContrastStatus, strFaceSnapMatch.fSimilarity);

        //获取人脸抓拍图片和对比图片,如果过小则存入提示消息，不存储了
        String faceSnapPic = "";
        if (strFaceSnapMatch.byPicTransType == 1) {
            log.error("[HKSDK][人脸比对][失败][{}]上报的抓拍图片的方式是URL请检查下摄像头配置的上报方式，设置成上报二进制图片", faceRecordUuid);
            return;
        }
        if(strFaceSnapMatch.struSnapInfo.dwSnapFacePicLen<0){
            recordMemo="设备上报的抓拍图片大小为0 ";
        }else {
            try {
                faceSnapPic = HIKHelper.saveImagePointerBuffer(strFaceSnapMatch.struSnapInfo.pBuffer1, strFaceSnapMatch.struSnapInfo.dwSnapFacePicLen, faceRecordUuid);
            } catch (Exception e) {
                e.printStackTrace();
                log.error("[HKSDK][人脸比对][失败][{}]人脸抓拍图片存储失败,原因:{}", faceRecordUuid,e.getMessage());
                return;
            }
        }

        //查找对应人脸库，过滤下评分低的就不去查找了
        FaceDb faceDb = null;
        if(strFaceSnapMatch.fSimilarity>0.8f){
            faceDb=faceDbMapper.selectOne(new QueryWrapper<FaceDb>().eq("device_uuid", hikDevice.getDevice().getUuid()).eq("device_db_sign", faceDbPicId));
        }

        //存储faceRecord
        recordMemo=recordMemo+"人脸库ID："+HIKHelper.getDeviceDbSign(faceDbId, faceDbPicId)+"，比对结果："+strFaceSnapMatch.byContrastStatus+"，相似度："+strFaceSnapMatch.fSimilarity;
        FaceRecord faceRecord = new FaceRecord();
        faceRecord.setUuid(faceRecordUuid);
        faceRecord.setFaceDeviceUuid(hikDevice.getDevice().getUuid());
        faceRecord.setFaceDbUuid(faceDb==null?"":faceDb.getUuid());
        faceRecord.setShotTime(HIKHelper.getFaceSnapTime(strFaceSnapMatch.struSnapInfo));
        faceRecord.setShotImage(faceSnapPic);
        faceRecord.setDbImage(faceDb==null?"":faceDb.getFaceImage());
        faceRecord.setDbType(faceDb==null?"stranger":faceDb.getType());
        faceRecord.setDbName(faceDb==null?"陌生人":faceDb.getName());
        faceRecord.setMemo(recordMemo);
        faceRecordMapper.insert(faceRecord);
        log.info("[HKSDK][人脸比对][{}]存入成功", faceRecordUuid);
    }


    //暂不使用这个报警接口
    private void faceCapture(Pointer pAlarmInfo) {
        log.info("人脸抓拍");
        HCNetSDK.NET_VCA_FACESNAP_RESULT strFaceSnapInfo = new HCNetSDK.NET_VCA_FACESNAP_RESULT();
        strFaceSnapInfo.write();
        Pointer pFaceSnapInfo = strFaceSnapInfo.getPointer();
        pFaceSnapInfo.write(0, pAlarmInfo.getByteArray(0, strFaceSnapInfo.size()), 0, strFaceSnapInfo.size());
        strFaceSnapInfo.read();

        //事件时间
        int dwYear = (strFaceSnapInfo.dwAbsTime >> 26) + 2000;
        int dwMonth = (strFaceSnapInfo.dwAbsTime >> 22) & 15;
        int dwDay = (strFaceSnapInfo.dwAbsTime >> 17) & 31;
        int dwHour = (strFaceSnapInfo.dwAbsTime >> 12) & 31;
        int dwMinute = (strFaceSnapInfo.dwAbsTime >> 6) & 63;
        int dwSecond = (strFaceSnapInfo.dwAbsTime >> 0) & 63;

        String strAbsTime = "" + String.format("%04d", dwYear) +
                String.format("%02d", dwMonth) +
                String.format("%02d", dwDay) +
                String.format("%02d", dwHour) +
                String.format("%02d", dwMinute) +
                String.format("%02d", dwSecond);

        //人脸属性信息
        String sFaceAlarmInfo = "Abs时间:" + strAbsTime + ",年龄:" + strFaceSnapInfo.struFeature.byAge +
                ",性别：" + strFaceSnapInfo.struFeature.bySex + ",是否戴口罩：" +
                strFaceSnapInfo.struFeature.byMask + ",是否微笑：" + strFaceSnapInfo.struFeature.bySmile;
        System.out.println("人脸信息：" + sFaceAlarmInfo);
        try {
            SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");//设置日期格式
            String time = df.format(new Date());// new Date()为获取当前系统时间

            //人脸图片写文件
            FileOutputStream small = new FileOutputStream("/home/el/tmp/" + time + "small.jpg");
            try {
                small.write(strFaceSnapInfo.pBuffer1.getByteArray(0, strFaceSnapInfo.dwFacePicLen), 0, strFaceSnapInfo.dwFacePicLen);
                small.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }

        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        }
    }
}
