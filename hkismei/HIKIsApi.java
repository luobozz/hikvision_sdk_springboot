package com.shtf.zfr.utils.hkismei;


import cn.hutool.core.util.XmlUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;

/**
 * 功能：透传接口实现，透传ISAPI命令
 */
public class HIKIsApi {

    /**
     * SDK透传ISAPI协议报文接口
     * @param lUserID
     * @param url
     * @param inputXml 输入报文
     * @return
     * @throws UnsupportedEncodingException
     */
    public static String isApi(HCNetSDK hcNetSDK,int lUserID, String url, String inputXml) throws UnsupportedEncodingException {
        String strOutXML = "";

        //输入参数
        HCNetSDK.NET_DVR_XML_CONFIG_INPUT struXMLInput = new HCNetSDK.NET_DVR_XML_CONFIG_INPUT();
        struXMLInput.read();

        struXMLInput.dwSize = struXMLInput.size();

        HCNetSDK.BYTE_ARRAY stringRequest = new HCNetSDK.BYTE_ARRAY(1024);
        stringRequest.read();

        //输入ISAPI协议命令   批量查询人脸库命令：GET /ISAPI/Intelligent/FDLib
        System.arraycopy(url.getBytes(), 0, stringRequest.byValue, 0, url.length());
        stringRequest.write();
        struXMLInput.lpRequestUrl = stringRequest.getPointer();
        struXMLInput.dwRequestUrlLen = url.length();

        //输入XML文本，GET命令不传输入文本
        int inputDataLen = inputXml.getBytes("UTF-8").length;

        if(inputDataLen > 0)
        {
            HCNetSDK.BYTE_ARRAY stringInBuffer = new HCNetSDK.BYTE_ARRAY(inputDataLen);
            stringInBuffer.read();
            System.arraycopy(inputXml.getBytes("UTF-8"), 0, stringInBuffer.byValue, 0, inputDataLen);
            stringInBuffer.write();
            struXMLInput.lpInBuffer = stringInBuffer.getPointer();
            struXMLInput.dwInBufferSize = inputDataLen;
        }
        else
        {
            struXMLInput.lpInBuffer = null;
            struXMLInput.dwInBufferSize = 0;
        }

        struXMLInput.write();

        HCNetSDK.BYTE_ARRAY stringXMLOut = new HCNetSDK.BYTE_ARRAY(8 * 1024);
        stringXMLOut.read();
        HCNetSDK.BYTE_ARRAY struXMLStatus = new HCNetSDK.BYTE_ARRAY(1024);
        struXMLStatus.read();

        HCNetSDK.NET_DVR_XML_CONFIG_OUTPUT struXMLOutput = new HCNetSDK.NET_DVR_XML_CONFIG_OUTPUT();
        struXMLOutput.read();
        struXMLOutput.dwSize = struXMLOutput.size();
        struXMLOutput.lpOutBuffer = stringXMLOut.getPointer();
        struXMLOutput.dwOutBufferSize = stringXMLOut.size();
        struXMLOutput.lpStatusBuffer = struXMLStatus.getPointer();
        struXMLOutput.dwStatusSize = struXMLStatus.size();
        struXMLOutput.write();

        if (!hcNetSDK.NET_DVR_STDXMLConfig(lUserID, struXMLInput, struXMLOutput)) {
            int iErr = hcNetSDK.NET_DVR_GetLastError();
//            System.err.println("NET_DVR_STDXMLConfig失败，错误号: " + iErr + "，URL: " + url);
            return null;
        } else {
            stringXMLOut.read();
//            System.out.println("输出文本大小：" + struXMLOutput.dwReturnedXMLSize);
            //打印输出XML文本
            strOutXML = new String(stringXMLOut.byValue).trim();
            //System.out.println(strOutXML);
            struXMLStatus.read();
            String strStatus = new String(struXMLStatus.byValue).trim();
//            System.out.println(strStatus);

            return strOutXML;
        }
    }
}
