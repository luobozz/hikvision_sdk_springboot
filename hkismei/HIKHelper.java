package com.shtf.zfr.utils.hkismei;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.XmlUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.shtf.zfr.bean.entity.FaceDb;
import com.shtf.zfr.bean.entity.FaceDevice;
import com.shtf.zfr.bean.exception.HIkException;
import com.shtf.zfr.constant.SystemConstants;
import com.shtf.zfr.utils.Helper;
import com.shtf.zfr.utils.IO.FileHelper;
import com.shtf.zfr.utils.encryption.MD5Helper;
import com.shtf.zfr.utils.hkismei.callBack.FACE_SHOT_NET_DVR_SetDVRMessageCallBack_V50;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.util.StringUtil;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;

@Data
@Slf4j
public class HIKHelper {


    private HCNetSDK hcNetSDK;
    private List<HIKDevice> hkdeivces;

    static FACE_SHOT_NET_DVR_SetDVRMessageCallBack_V50 face_shot_net_dvr_setDVRMessageCallBack_v50 = null;


    public HIKHelper() {
        this.hcNetSDK = null;
        this.hkdeivces = new ArrayList<HIKDevice>();
    }

    public Boolean initSuccess() {
        return hcNetSDK != null && hcNetSDK.NET_DVR_Init();
    }

    public HIKDevice getDeviceByLoginId(int loginId) {
        return this.hkdeivces.stream().filter(fi -> fi.getLoginId() == loginId).findFirst().orElse(null);
    }

    public HIKDevice getDeviceByDeviceUuid(String uuid) {
        return this.hkdeivces.stream().filter(fi -> fi.getDevice().getUuid().equals(uuid)).findFirst().orElse(null);
    }

    public boolean hasFaceDb(int lUserID, String deviceUuid) {
        return getCustomFaceLib(lUserID).stream().filter(fi -> fi.get("name").equals(SystemConstants.FaceConstants.FACE_LIB_NAME) && fi.get("customFaceLibID").equals(deviceUuid)).findAny().isPresent();
    }

    public boolean creatFaceDb(int lUserID, String deviceUuid) throws UnsupportedEncodingException {
        String result = HIKIsApi.isApi(this.hcNetSDK, lUserID, "POST /ISAPI/Intelligent/FDLib?FDType=custom", HIKCommon.XmlCreatCustomID(SystemConstants.FaceConstants.FACE_LIB_NAME, deviceUuid));
        if (result == null) {
            return false;
        }
        Map<String, Object> xmlMap = XmlUtil.xmlToMap(result);
        Object FDLibInfo = xmlMap.get("FDLibInfo");
        if (FDLibInfo == null) {
            return false;
        } else {
            return !StringUtils.isEmpty(JSON.parseObject(JSON.toJSONString(FDLibInfo)).getString("id"));
        }
    }

    public String createFaceDbImage(FaceDb faceDb) throws IOException {
        String dbDeviceSign=null;
        HIKDevice hikDevice = this.getDeviceByDeviceUuid(faceDb.getDeviceUuid());
        if (hikDevice == null || !hikDevice.isLogin()) {
            log.error("设备{}，创建人脸失败：{}，原因：内存中没有找到已经登陆的设备，请检查设备登陆状态(页面重发/启动或者重启应用)", faceDb.getDeviceUuid(), faceDb.getName());
            throw new HIkException("内存中没有找到已经登陆的设备，请检查设备登陆状态(页面重发/启动或者重启应用)");
        }

        HCNetSDK.NET_DVR_FACELIB_COND struFaceLibCond = new HCNetSDK.NET_DVR_FACELIB_COND();
        struFaceLibCond.read();
        struFaceLibCond.dwSize = struFaceLibCond.size();
        struFaceLibCond.szFDID = faceDb.getDeviceUuid().getBytes(); //人脸库ID
        struFaceLibCond.byCustomFaceLibID = 1;  //人脸库ID是否是自定义：0- 不是，1- 是
//        struFaceLibCond.byIdentityKey= IdentityKey.getBytes();  //交互操作口令  和自定义添加人脸库的IdentityKey保持一致
        struFaceLibCond.byConcurrent = 0; //设备并发处理：0- 不开启(设备自动会建模)，1- 开始(设备不会自动进行建模)
        struFaceLibCond.byCover = 1;  //是否覆盖式导入(人脸库存储满的情况下强制覆盖导入时间最久的图片数据)：0- 否，1- 是
        struFaceLibCond.write();
        Pointer pStruFaceLibCond = struFaceLibCond.getPointer();
        int iUploadHandle = this.hcNetSDK.NET_DVR_UploadFile_V40(hikDevice.getLoginId(), HCNetSDK.IMPORT_DATA_TO_FACELIB, pStruFaceLibCond,
                struFaceLibCond.size(), null, Pointer.NULL, 0);
        if (iUploadHandle <= -1) {
            log.error("设备{}，创建人脸失败：{}，原因：NET_DVR_UploadFile_V40失败，错误号{}", faceDb.getDeviceUuid(), faceDb.getName(), this.hcNetSDK.NET_DVR_GetLastError());
            throw new HIkException("NET_DVR_UploadFile_V40失败");
        }
        HCNetSDK.NET_DVR_SEND_PARAM_IN struSendParam = new HCNetSDK.NET_DVR_SEND_PARAM_IN();
        struSendParam.read();
        //本地jpg图片转成二进制byte数组
        byte[] picbyte = HIKCommon.toByteArray(SystemConstants.RESOURCE_HOME + faceDb.getFaceImage());
        HCNetSDK.BYTE_ARRAY arraybyte = new HCNetSDK.BYTE_ARRAY(picbyte.length);
        arraybyte.read();
        arraybyte.byValue = picbyte;
        arraybyte.write();
        struSendParam.pSendData = arraybyte.getPointer();
        struSendParam.dwSendDataLen = picbyte.length;
        struSendParam.byPicType = 1; //图片格式：1- jpg，2- bmp，3- png，4- SWF，5- GIF
        struSendParam.sPicName =faceDb.getName().getBytes(); //图片名称
        //图片的附加信息缓冲区  图片上添加的属性信息，性别、身份等
        //1:xml文本导入方式
/**        byte[] AppendData = CommonMethod.toByteArray("..\\pic\\test.xml");
 HCNetSDK.BYTE_ARRAY byteArray = new HCNetSDK.BYTE_ARRAY(AppendData.length);
 byteArray.read();
 byteArray.byValue = AppendData;
 byteArray.write();*/
        /**2:包含中文姓名的报文上传
         <customHumanID>ID20220109</customHumanID> 表示自定义人脸ID*/
        byte[] byFDLibName = faceDb.getName().getBytes("UTF-8");
        byte[] byCustomHumanId = faceDb.getUuid().getBytes("UTF-8");
        String strInBuffer1 = new String("<FaceAppendData version=\"2.0\" xmlns=\"http://www.hikvision.com/ver20/XMLSchema\"><name>");
        String strInBuffer2 = new String("</name><customHumanID>");
        String strInBuffer3 = new String("</customHumanID></FaceAppendData>");
        int iStringSize = strInBuffer1.length() + byFDLibName.length+strInBuffer2.length()+byCustomHumanId.length+strInBuffer3.length();
        HCNetSDK.BYTE_ARRAY ptrByte = new HCNetSDK.BYTE_ARRAY(iStringSize);
        System.arraycopy(strInBuffer1.getBytes(), 0, ptrByte.byValue, 0, strInBuffer1.length());
        System.arraycopy(byFDLibName, 0, ptrByte.byValue, strInBuffer1.length(), byFDLibName.length);
        System.arraycopy(strInBuffer2.getBytes(), 0, ptrByte.byValue, strInBuffer1.length() + byFDLibName.length, strInBuffer2.length());
        System.arraycopy(byCustomHumanId, 0, ptrByte.byValue, strInBuffer1.length() + byFDLibName.length+strInBuffer2.length(), byCustomHumanId.length);
        System.arraycopy(strInBuffer3.getBytes(), 0, ptrByte.byValue, strInBuffer1.length() + byFDLibName.length+strInBuffer2.length()+byCustomHumanId.length, strInBuffer3.length());
        ptrByte.write();
        struSendParam.pSendAppendData = ptrByte.getPointer();
        struSendParam.dwSendAppendDataLen = ptrByte.byValue.length;
        struSendParam.write();
        int iSendData = this.hcNetSDK.NET_DVR_UploadSend(hikDevice.getLoginId(), struSendParam, Pointer.NULL);
        if (iSendData <= -1) {
            int iErr = this.hcNetSDK.NET_DVR_GetLastError();
            log.error("设备{}，创建人脸失败：{}，原因：", faceDb.getDeviceUuid(), faceDb.getName(), iErr);
            throw new HIkException("NET_DVR_UploadSend失败");
        }


        while (true) {
            IntByReference Pint = new IntByReference(0);
            int state = this.hcNetSDK.NET_DVR_GetUploadState(iSendData, Pint.getPointer());
//            log.info("上传状态{}",state);
            if (state == 2) {
//                log.info("设备{}，创建人脸：{},上传人脸图片中，进度：", faceDb.getDeviceUuid(), faceDb.getName(),Pint.getValue());
                continue;
            }else if(state==1){
//                log.info("设备{}，创建人脸：{},上传人脸图片成功", faceDb.getDeviceUuid(), faceDb.getName());
                //获取图片ID
                HCNetSDK.NET_DVR_UPLOAD_FILE_RET struUploadRet = new HCNetSDK.NET_DVR_UPLOAD_FILE_RET();
                boolean bUploadResult = this.hcNetSDK.NET_DVR_GetUploadResult(iUploadHandle, struUploadRet.getPointer(), struUploadRet.size());
                if (!bUploadResult) {
                    log.error("设备{}，创建人脸失败：{}，原因：", faceDb.getDeviceUuid(), faceDb.getName(), "NET_DVR_GetUploadResult失败，错误号" + this.hcNetSDK.NET_DVR_GetLastError());
                } else {
                    struUploadRet.read();
                    dbDeviceSign= Helper.byte2Str(struUploadRet.sUrl);
                }
                break;
            }else {
                //先关闭连接再抛出异常
                closeUploadConn(iUploadHandle,faceDb);
                throw new HIkException(getUploadStatus(state));
            }
        }
        //正常后抛出关闭连接
        closeUploadConn(iUploadHandle,faceDb);
        return dbDeviceSign;
    }

    public boolean delFacePicBycustomID(FaceDb faceDb) throws Exception {
        HIKDevice hikDevice = this.getDeviceByDeviceUuid(faceDb.getDeviceUuid());
        if (hikDevice == null || !hikDevice.isLogin()) {
            log.error("设备{}，删除人脸失败：{}，原因：内存中没有找到已经登陆的设备，请检查设备登陆状态(页面重发/启动或者重启应用)", faceDb.getDeviceUuid(), faceDb.getName());
            throw new HIkException("内存中没有找到已经登陆的设备，请检查设备登陆状态(页面重发/启动或者重启应用)");
        }
        String requestUrl = "DELETE /ISAPI/Intelligent/FDLib/" + faceDb.getDeviceUuid()+ "/picture/" + faceDb.getUuid()+"?FDType=custom";
        String result=HIKIsApi.isApi(this.hcNetSDK,hikDevice.getLoginId(), requestUrl, "");
        return result!=null;
    }


    private void closeUploadConn(int iUploadHandle,FaceDb faceDb){
        //关闭图片上传连接
        boolean b_Close = this.hcNetSDK.NET_DVR_UploadClose(iUploadHandle);
        if (!b_Close) {
            int iErr = this.hcNetSDK.NET_DVR_GetLastError();
            log.error("设备{}，创建人脸失败：{}，原因：", faceDb.getDeviceUuid(), faceDb.getName(), "NET_DVR_UploadSend失败，错误号" + iErr);
            throw new HIkException("NET_DVR_UploadClose失败");
        }
    }



    public List<HashMap> getCustomFaceLib(int lUserID) {
        try {
            String result = HIKIsApi.isApi(this.hcNetSDK, lUserID, "GET /ISAPI/Intelligent/FDLib/", "");
            if (result == null) {
                return new ArrayList<>();
            }
            Map<String, Object> xmlMap = XmlUtil.xmlToMap(result);
            Object FDLibBaseCfg = xmlMap.get("FDLibBaseCfg");
            if (FDLibBaseCfg == null) {
                return new ArrayList<>();
            }
            if (FDLibBaseCfg instanceof ArrayList) {
                //不是单个的回文
                return (List<HashMap>) FDLibBaseCfg;
            } else {
                //单个的回文
                List<HashMap> reList = new ArrayList<>();
                reList.add((HashMap) FDLibBaseCfg);
                return reList;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * 设备登录V40 与V30功能一致
     */
    public HIKDevice Login(FaceDevice device) throws Exception {
        //注册
        HCNetSDK.NET_DVR_USER_LOGIN_INFO m_strLoginInfo = new HCNetSDK.NET_DVR_USER_LOGIN_INFO();//设备登录信息
        HCNetSDK.NET_DVR_DEVICEINFO_V40 m_strDeviceInfo = new HCNetSDK.NET_DVR_DEVICEINFO_V40();//设备信息

        String m_sDeviceIP = device.getIp();//设备ip地址
        m_strLoginInfo.sDeviceAddress = new byte[HCNetSDK.NET_DVR_DEV_ADDRESS_MAX_LEN];
        System.arraycopy(m_sDeviceIP.getBytes(), 0, m_strLoginInfo.sDeviceAddress, 0, m_sDeviceIP.length());

        String m_sUsername = device.getApiUsername();//设备用户名
        m_strLoginInfo.sUserName = new byte[HCNetSDK.NET_DVR_LOGIN_USERNAME_MAX_LEN];
        System.arraycopy(m_sUsername.getBytes(), 0, m_strLoginInfo.sUserName, 0, m_sUsername.length());

        String m_sPassword = device.getApiPassword();//设备密码
        m_strLoginInfo.sPassword = new byte[HCNetSDK.NET_DVR_LOGIN_PASSWD_MAX_LEN];
        System.arraycopy(m_sPassword.getBytes(), 0, m_strLoginInfo.sPassword, 0, m_sPassword.length());

        m_strLoginInfo.wPort = 8000;
        m_strLoginInfo.bUseAsynLogin = false; //是否异步登录：0- 否，1- 是
        m_strLoginInfo.write();

        int loginid = hcNetSDK.NET_DVR_Login_V40(m_strLoginInfo, m_strDeviceInfo);
        if (loginid >= 0) {
            HIKDevice hkDevice = new HIKDevice(device, loginid);
            this.hkdeivces.add(hkDevice);
            return hkDevice;
        } else {
            return null;
        }
    }

    public Boolean setAlarmCallBack() {
        face_shot_net_dvr_setDVRMessageCallBack_v50 = new FACE_SHOT_NET_DVR_SetDVRMessageCallBack_V50();
        return this.hcNetSDK.NET_DVR_SetDVRMessageCallBack_V50(0, face_shot_net_dvr_setDVRMessageCallBack_v50, null);
    }

    public Boolean setAlarmChan(int lUserID) {
        //启用布防
        HCNetSDK.NET_DVR_SETUPALARM_PARAM struAlarmParam = new HCNetSDK.NET_DVR_SETUPALARM_PARAM();
        struAlarmParam.dwSize = struAlarmParam.size();
        struAlarmParam.byFaceAlarmDetection = 0; //人脸抓拍报警，上传COMM_UPLOAD_FACESNAP_RESULT类型报警信息
        //其他报警布防参数不需要设置，不支持
        return this.hcNetSDK.NET_DVR_SetupAlarmChan_V41(lUserID, struAlarmParam) >= 0;
    }

    public static byte[] getValFromPointerByte(Pointer pointer, int pointerLen) {
        pointerLen = pointerLen - 1;
        byte[] bytes1 = new byte[pointerLen];
        if (pointerLen > 0) {
            ByteBuffer buffers1 = pointer.getByteBuffer(0, pointerLen);
            buffers1.get(bytes1);
        }
        return bytes1;
    }

    public static String getValFromPointerStr(Pointer pointer, int pointerLen) {
        try {
            return new String(getValFromPointerByte(pointer, pointerLen));
        } catch (Exception e) {
            return "";
        }
    }

    public static String saveImagePointerBuffer(Pointer bufferPointer, int bufferPointerLen, String faceRecordUuid) throws IOException {
        String path = getResourcePath(faceRecordUuid);
        FileOutputStream fout = null;
        try {
            //将字节写入文件
            long offset = 0;
            ByteBuffer buffers = bufferPointer.getByteBuffer(offset, bufferPointerLen);
            byte[] bytes = new byte[bufferPointerLen];
            buffers.rewind();
            buffers.get(bytes);
            checkPath(path);
            fout = new FileOutputStream(path);
            fout.write(bytes);
            fout.close();
            return getSourceRelativePath(path);
        } catch (Exception e) {
            fout.close();
            throw e;
        }
    }


    private static void checkPath(String path) {
        File file = new File(path);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
    }

    private static String getResourcePath(String faceRecordUuid) {
        LocalDate today = LocalDate.now();
        String resParentPath = String.format("%s/%s/%s", today.getYear(), today.getMonthValue(), today.getDayOfMonth());
        return String.format("%s/%s/%s.jpg", SystemConstants.ResourcesConstants.RESOURCE_FACE_HOME, resParentPath, faceRecordUuid);
    }

    private static String getSourceRelativePath(String path) {
        if (!(path.indexOf(SystemConstants.RESOURCE_HOME) == 0)) {
            return path;
        }
        path = path.replaceAll("\\\\", "/");
        return path.substring(SystemConstants.RESOURCE_HOME.length(), path.length());
    }

    private String getUploadStatus(int status){
        switch (status){
            case 1:
                return "上传成功";
            case 2:
                return "正在上传	不需要处理";
            case 3:
                return "上传失败";
            case 4:
                return "网络断开，状态未知";
            case 6:
                return "硬盘错误";
            case 7:
                return "无审讯文件存放盘";
            case 8:
                return "容量不足";
            case 9:
                return "设备资源不足";
            case 10:
                return "文件个数超过最大值";
            case 11:
                return "文件过大";
            case 12:
                return "文件类型错误";
            case 19:
                return "文件格式不正确";
            case 20:
                return "文件内容不正确";
            case 21:
                return "上传音频采样率不支持";
            case 26:
                return "名称错误";
            case 27:
                return "图片分辨率无效错误";
            case 28:
                return "图片目标个数超过上限";
            case 29:
                return "图片未识别到目标";
            case 30:
                return "图片数据识别失败";
            case 31:
                return "分析引擎异常";
            case 32:
                return "解析图片数据出错";
            case 34:
                return "安全校验密钥错误";
            case 35:
                return "图片URL未开始下载";
            case 36:
                return "自定义人员ID重复";
            case 37:
                return "自定义人员ID有误";
            case 38:
                return "建模失败,设备内部错误";
            case 39:
                return "建模失败，人脸建模错误";
            case 40:
                return "建模失败，人脸质量评分错误";
            case 41:
                return "建模失败，特征点提取错误";
            case 42:
                return "建模失败，属性提取错误";
            case 43:
                return "图片数据错误";
            case 44:
                return "图片附加信息错误";
            default:
                return "";
        }
    }

    public static String getDeviceDbSign(String faceDbId, String faceDbPicId) {
        return String.format("%s-%s", faceDbId, faceDbPicId);
    }

    public static String getFaceSnapTime(HCNetSDK.NET_VCA_FACESNAP_INFO_ALARM struSnapInfo) {
        //事件时间
        int dwYear = (struSnapInfo.dwAbsTime >> 26) + 2000;
        int dwMonth = (struSnapInfo.dwAbsTime >> 22) & 15;
        int dwDay = (struSnapInfo.dwAbsTime >> 17) & 31;
        int dwHour = (struSnapInfo.dwAbsTime >> 12) & 31;
        int dwMinute = (struSnapInfo.dwAbsTime >> 6) & 63;
        int dwSecond = (struSnapInfo.dwAbsTime >> 0) & 63;

        return String.format("%04d-%02d-%02d %02d:%02d:%02d", dwYear, dwMonth, dwDay, dwHour, dwMinute, dwSecond);
    }
}
