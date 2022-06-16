package com.shtf.zfr.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.shtf.zfr.bean.entity.FaceDevice;
import com.shtf.zfr.mapper.FaceDbMapper;
import com.shtf.zfr.mapper.FaceDeviceMapper;
import com.shtf.zfr.utils.hkismei.HCNetSDK;
import com.shtf.zfr.utils.hkismei.HIKDevice;
import com.shtf.zfr.utils.hkismei.HIKHelper;
import com.shtf.zfr.utils.hkismei.callBack.FACE_SHOT_NET_DVR_SetDVRMessageCallBack_V50;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.examples.win32.W32API;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.util.List;


@Configuration
@Slf4j
public class HikLibConfig {
    @Value("${hik-lib.path}")
    private String hikLibPath;
    @Value("${hik-lib.log-path}")
    private String hikLogPath;
    @Autowired
    private FaceDeviceMapper faceDeviceMapper;
    @Autowired
    private FaceDbMapper faceDbMapper;

    @Bean
    public HIKHelper initHkSDK() throws IOException {
        //加载海康SDK
        HIKHelper hikHelper = new HIKHelper();
        File directory = new File("./");
        String path = directory.getCanonicalPath() + File.separator + hikLibPath;
        log.info("[HKSDK]加载海康SDK:[{}]", path);
        hikHelper.setHcNetSDK((HCNetSDK) Native.loadLibrary(path, HCNetSDK.class));
        hikHelper.getHcNetSDK().NET_DVR_SetConnectTime(2000, 1);
        hikHelper.getHcNetSDK().NET_DVR_SetReconnect(10000, true);
        if (!hikHelper.initSuccess()) {
            log.error("[HKSDK]海康sdk加载失败");
            return hikHelper;
        }

        //开启日志
        hikHelper.getHcNetSDK().NET_DVR_SetLogToFile(3, hikLogPath, false);
        //设置报警回调函数
        if (!hikHelper.setAlarmCallBack()) {
            log.error("[HKSDK]海康sdk设置回调函数失败");
            return hikHelper;
        }


        List<FaceDevice> faceDeviceList = faceDeviceMapper.selectList(new QueryWrapper<FaceDevice>().eq("type", "face").eq("active_flag", 1));
        faceDeviceList.forEach(fo -> {
            try {
                //设备登陆
                HIKDevice hikDevice = hikHelper.Login(fo);
                if (hikDevice != null && hikDevice.isLogin()) {
                    log.info("[HKSDK]设备：{}{}，登陆成功，id：{}，ip：{}", fo.getUuid(), fo.getName(), hikDevice.getLoginId(), fo.getIp());
                    //设备布防
                    if (hikHelper.setAlarmChan(hikDevice.getLoginId())) {
                        hikDevice.setAlarmChecked(true);
                        hikDevice.getDevice().setDeviceSign(hikDevice.getLoginId());
                        log.info("[HKSDK]设备：{}{}，布防成功，id：{}，ip：{}", fo.getUuid(), fo.getName(), hikDevice.getLoginId(), fo.getIp());
                    } else {
                        hikDevice.setAlarmChecked(false);
                        hikDevice.getDevice().setDeviceSign(null);
                        log.error("[HKSDK]设备：{}{}，布防失败，id：{}，错误码为{}", fo.getUuid(), fo.getName(), hikDevice.getLoginId(), fo.getIp(), hikHelper.getHcNetSDK().NET_DVR_GetLastError());
                    }
                    //检查人脸库
                    if (hikHelper.hasFaceDb(hikDevice.getLoginId(), hikDevice.getDevice().getUuid())) {
                        hikDevice.setFaceDbChecked(true);
                        log.info("[HKSDK]设备：{}{}，检查人脸库成功，id：{}，ip：{}", fo.getUuid(), fo.getName(), hikDevice.getLoginId(), fo.getIp());
                    } else {
                        hikDevice.setFaceDbChecked(false);
                        //重置所有已经下发的人脸库的人脸为未下发
                        faceDbMapper.resetAllByDevice(hikDevice.getDevice().getUuid());
                        //重新创建专用库
                        if (hikHelper.creatFaceDb(hikDevice.getLoginId(), hikDevice.getDevice().getUuid())) {
                            hikDevice.setFaceDbChecked(true);
                            log.info("[HKSDK]设备：{}{}，检查人脸库成功(原人脸库丢失，已经重新创建成功)，id：{}，ip：{}", fo.getUuid(), fo.getName(), hikDevice.getLoginId(), fo.getIp());
                        } else {
                            hikDevice.setFaceDbChecked(false);
                            log.error("[HKSDK]设备：{}{}，检查人脸库失败，id：{}，错误码为没找到专用人脸库", fo.getUuid(), fo.getName(), hikDevice.getLoginId(), fo.getIp());
                        }
                    }
                    faceDeviceMapper.updateById(hikDevice.getDevice());
                } else {
                    log.error("[HKSDK]设备：{}{}，登录失败，错误码为{}", fo.getUuid(), fo.getName(), hikHelper.getHcNetSDK().NET_DVR_GetLastError());
                }
            } catch (Exception e) {
                e.printStackTrace();
                log.error("[HKSDK]设备：{}{}，初始化异常，错误码为{}", fo.getUuid(), fo.getName(), e.getMessage());
            }
        });


        return hikHelper;
    }
}
