package com.shtf.zfr.utils.hkismei;


import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import java.io.*;

public class HIKCommon {

    /**
     * 读取本地文件到数组中
     *
     * @param filename 本地文件
     * @return 返回读取到的数据到 byte数组
     * @throws IOException
     */
    public static byte[] toByteArray(String filename) throws IOException {
        File file = new File(filename);
        if (!file.exists()) {
            throw new FileNotFoundException(filename);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream((int) file.length());
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
        try {
            byte[] buffer = new byte[1024];
            int len;
            while (-1 != (len = in.read(buffer, 0, buffer.length))) {
                bos.write(buffer, 0, len);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        } finally {
            bos.close();
            in.close();
        }
    }


    //int 转化为字节数组
    public static byte[] intTobyte2(int num)
    {
        byte[] result=null;
        ByteArrayOutputStream bos=new ByteArrayOutputStream();
        DataOutputStream dos=new DataOutputStream(bos);
        try {
            dos.writeInt(num);
            result=bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    //  创建人脸库输入XML报文
    public static String XmlCreatCustomID( String Name,String cusId) {
        /*<CreateFDLibList version="2.0" xmlns="http://www.isapi.org/ver20/XMLSchema">
        <!--req,创建人脸比对库输入参数-->
        <CreateFDLib>
        <id>
        <!--req, xs:integer,"表示list中子项个数,从"1"开始赋值,依次增加" -->
        </id>
        <name>
        <!--opt, xs:string,"人脸比对库名称"-->
        </name>
        <thresholdValue>
        <!--opt, xs:integer, "检测阈值,阈值越大检测准确率越低, 范围[0,100]"-->
        </thresholdValue>
        <customInfo>
        <!--opt, xs:string, 人脸库附加信息-->
        </customInfo>
        <customFaceLibID>
        <!--opt, xs:string, "自定义人脸库ID, 由上层下发给设备, 该ID由上层维护并确保唯一性,
        设备侧需将自定义人脸库ID与设备生成的FDID进行关联, 确保上层可通过下发人脸库ID来替代下发FDID进行后续操作"-->
            </customFaceLibID>
        </CreateFDLib>
        </CreateFDLibList>*/
        Document document1;
        Element root = DocumentHelper.createElement("CreateFDLibList");
        document1 = DocumentHelper.createDocument(root);
        Element CreateFDLib = root.addElement("CreateFDLib");
//        Element id = CreateFDLib.addElement("id");
//        id.setText(ID);
        Element name = CreateFDLib.addElement("name");
        name.setText(Name);
        Element thresholdValue = CreateFDLib.addElement("thresholdValue");
        thresholdValue.setText("1");
        Element CustomFaceLibID = CreateFDLib.addElement("customFaceLibID");
        CustomFaceLibID.setText(cusId);
        String requestXml = document1.asXML();
        return requestXml;
    }

    //查询人脸比对数据的输入XML报文
    public static String FCSearch_XmlCreat() {
        Document document1;
        Element root = DocumentHelper.createElement("FCSearchDescription");
        document1 = DocumentHelper.createDocument(root);
        Element searchID = root.addElement("searchID");
        searchID.setText("C929433A-AD10-0001-CA62-1A701E0015F9");
        Element searchResultPosition = root.addElement("searchResultPosition");
        searchResultPosition.setText("0");
        Element maxResults = root.addElement("maxResults");
        maxResults.setText("50");
        Element FDID = root.addElement("FDID");
        FDID.setText("6C5791A3708B4C418388E05FCD6F6BD9");
        Element snapStartTime = root.addElement("snapStartTime");
        snapStartTime.setText("2021-04-14T17:00:00Z");
        Element snapEndTime = root.addElement("snapEndTime");
        snapEndTime.setText("2021-04-15T11:53:00Z");
        Element faceMatchInfoEnable = root.addElement("faceMatchInfoEnable");
        faceMatchInfoEnable.setText("true");
        Element eventType = root.addElement("eventType");
        eventType.setText("whiteFaceContrast");
        Element sortord = root.addElement("sortord");
        sortord.setText("time");
        String requestXml = document1.asXML();
        return requestXml;
    }
}