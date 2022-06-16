package com.shtf.zfr.utils.hkismei;

import com.shtf.zfr.bean.entity.FaceDevice;
import com.sun.jna.NativeLong;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HIKDevice {
    private FaceDevice device;
    private int loginId;

    private boolean alarmChecked;
    private boolean faceDbChecked;

    public HIKDevice(FaceDevice device,int loginId){
        this.device=device;
        this.loginId=loginId;
        this.alarmChecked=false;
        this.faceDbChecked=false;
    }


    public Boolean isLogin(){
        return this.loginId>=0;
    }
}
