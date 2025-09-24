package org.youkill;//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import nc.bs.framework.common.InvocationInfo;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.framework.common.Profiler;
import nc.bs.framework.common.RuntimeEnv;
import nc.bs.framework.comn.NetObjectInputStream;
import nc.bs.framework.comn.NetObjectOutputStream;
import nc.bs.framework.comn.NetStreamConstants;
import nc.bs.framework.comn.Result;
import nc.bs.framework.comn.cdr.NcRecorder;
import nc.bs.framework.comn.cdr.Round;
import nc.bs.framework.comn.serv.ServiceHandler;
import nc.bs.framework.component.RemoteProcessComponetFactory;
import nc.bs.framework.core.Server;
import nc.bs.framework.exception.ComponentException;
import nc.bs.framework.exception.FrameworkRuntimeException;
import nc.bs.framework.naming.Context;
import nc.bs.framework.server.token.TokenUtil;
import nc.bs.logging.Log;
import nc.bs.logging.Logger;
import nc.vo.pub.BusinessRuntimeException;

public class ServiceDispatcher2 implements ServiceHandler {
    private final Log log = Log.getInstance(ServiceDispatcher2.class);
    private static final MessageFormat enterMethodMsgFormat = new MessageFormat("enter server invoke bean: {0} MethodName: {1}");
    private static final MessageFormat leaveMethodMsgFormat = new MessageFormat("leave server invoke bean: {0} MethodName: {1}, spend time {2}");
    private static final MessageFormat writeResultFormat = new MessageFormat("write result to client for {0} on method {1} take time: {2}");
    private Context remoteCtx;
    private Map ctxMap = new HashMap(128);
    private RemoteProcessComponetFactory factory = null;

    public ServiceDispatcher2() {
        Properties props = new Properties();
        props.setProperty("nc.locator.provider", "nc.bs.framework.server.RemoteNCLocator");
        this.remoteCtx = NCLocator.getInstance(props);

        try {
            this.factory = (RemoteProcessComponetFactory)NCLocator.getInstance().lookup("RemoteProcessComponetFactory");
        } catch (Throwable var3) {
            this.log.warn("RemoteCallPostProcess is not found");
        }

    }

    public void writeCDR(Round request, Round round) {
        try {
            NcRecorder.writeCDR(request, round);
        } catch (IOException var4) {
            this.log.warn("NC_CDR fearure is not avaiable here:", var4);
        }

    }

    public void execCall(HttpServletRequest request, HttpServletResponse response) throws Throwable {
//        ThreadTracer.getInstance().startThreadMonitor("initinvoke", request.getRemoteAddr(), "anonymous");
        InvocationInfo invInfo = null;
        Round round = null;
        Result result = new Result();
        boolean inited = false;
        boolean[] streamRet = new boolean[]{NetStreamConstants.STREAM_NEED_COMPRESS, NetStreamConstants.STREAM_NEED_ENCRYPTED};

        try {
            try {
                int[] lsizes = new int[1];

                long wBeginTime;
                try {
                    wBeginTime = System.currentTimeMillis();
                    invInfo = (InvocationInfo)readObject(request.getInputStream(), streamRet, lsizes);
                    if (invInfo != null) {
                        round = new Round(invInfo);
                        this.writeCDR(round, round);
                    }

                    String callId = invInfo.getCallId();
                    Logger.putMDC("serial", callId);
                    if (Profiler.log.isDebugEnabled()) {
                        String svc = "";
                        svc = invInfo.getServicename() + "." + invInfo.getMethodName();
                        Profiler.log.debug(svc + " flowsize: " + lsizes[0] + " read net spends time: " + (System.currentTimeMillis() - wBeginTime));
                    }

                    invInfo.setServerName(((Server)NCLocator.getInstance().lookup(Server.class)).getServerName());
                    invInfo.setServerHost(request.getServerName());
                    invInfo.setServerPort(request.getServerPort());
                    invInfo.setRemoteHost(request.getRemoteAddr());
                    invInfo.setRemotePort(request.getRemotePort());
                    Logger.setUserLevel(invInfo.getUserLevel());
                } catch (ClassNotFoundException var22) {
                    result.appexception = new FrameworkRuntimeException("Unexpected error(ClassNotFound)", var22);
                    this.writeResult(round, result, streamRet[0], streamRet[1], response, inited);
                    if (result.appexception == null) {
                        this.postRemoteProcess();
                    }

                    if (this.factory != null) {
                        this.factory.clearThreadScopePostProcess();
                    }

                    result = null;
                    RuntimeEnv.getInstance().setThreadRunningInServer(true);
//                    ThreadTracer.getInstance().endThreadMonitor();
                    Logger.setUserLevel((String)null);
                    return;
                } catch (InvalidClassException var23) {
                    result.appexception = new FrameworkRuntimeException("Unexpected error(InvalidClass)", var23);
                    this.writeResult(round, result, streamRet[0], streamRet[1], response, inited);
                    if (result.appexception == null) {
                        this.postRemoteProcess();
                    }

                    if (this.factory != null) {
                        this.factory.clearThreadScopePostProcess();
                    }

                    result = null;
                    RuntimeEnv.getInstance().setThreadRunningInServer(true);
//                    ThreadTracer.getInstance().endThreadMonitor();
                    Logger.setUserLevel((String)null);
                    return;
                }

                Logger.init(invInfo.getServiceName());
                inited = true;
                InvocationInfoProxy.getInstance().set(invInfo);
                this.traceForMonitor(invInfo);
                this.preRemoteProcess();

                try {
                    TokenUtil.getInstance().vertifyToken(invInfo.getToken(), invInfo.getServiceName(), this.getAddr(request));
                    result.result = this.invokeBeanMethod(invInfo.getModule(), invInfo.getServiceName(), invInfo.getMethodName(), invInfo.getParametertypes(), invInfo.getParameters());
                } catch (InvocationTargetException var20) {
                    Throwable appException = extractException(var20);
                    if (appException instanceof RuntimeException) {
                        Logger.error("应用运行异常", appException);
                        if (!(appException instanceof FrameworkRuntimeException) && !verifyThrowable(appException)) {
                            Logger.error("服务调用异常", appException);
                            result.appexception = new BusinessRuntimeException(appException.getMessage());
                        } else {
                            result.appexception = appException;
                        }
                    } else {
                        result.appexception = appException;
                    }
                } catch (Throwable var21) {
                    this.log.error(var21.getMessage(), var21);
                    if (var21 instanceof BusinessRuntimeException) {
                        result.appexception = var21;
                    } else if (var21 instanceof FrameworkRuntimeException) {
                        result.appexception = var21;
                    } else {
                        result.appexception = this.warpThrowable("Unexpected error", var21);
                    }
                }

                if (result.appexception != null) {
                    this.postErrorRemoteProcess(result.appexception);
                }

                wBeginTime = System.currentTimeMillis();
                this.writeResult(round, result, streamRet[0], streamRet[1], response, inited);
                long wTakeTime = System.currentTimeMillis() - wBeginTime;
                if (Profiler.log.isDebugEnabled()) {
                    Profiler.log.debug(writeResultFormat.format(new Object[]{invInfo.getServiceName(), invInfo.getMethodName(), wTakeTime}));
                    return;
                }
            } catch (Throwable var24) {
                if (inited) {
                    Logger.error("Unexpected error occurred, maybe network error", var24);
                } else {
                    this.log.error("Unexpected error occurred, maybe network error", var24);
                }

                return;
            }

        } finally {
            if (result.appexception == null) {
                this.postRemoteProcess();
            }

            if (this.factory != null) {
                this.factory.clearThreadScopePostProcess();
            }

            result = null;
            RuntimeEnv.getInstance().setThreadRunningInServer(true);
//            ThreadTracer.getInstance().endThreadMonitor();
            Logger.setUserLevel((String)null);
        }
    }

    public String getAddr(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }

        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }

        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        return ip;
    }

    private void writeResult(Round round, Result result, boolean compressed, boolean encrypted, HttpServletResponse response, boolean inited) throws IOException {
        try {
            InvocationInfo info = InvocationInfoProxy.getInstance().get();
            String svc = "";
            if (info != null) {
                svc = info.getServicename() + "." + info.getMethodName();
            }

            if (result.appexception != null) {
                if (inited) {
                    Logger.error("Exception that throw to client", result.appexception);
                } else {
                    this.log.error("Exception that throw to client", result.appexception);
                }
            }

            long now = System.currentTimeMillis();
            if (round != null) {
                Round rRound = new Round();
                rRound.setResult(result);
                rRound.setRound_id(round.getRound_id());
                this.writeCDR(round, rRound);
            }

//            ThreadTracer.getInstance().updateEvent("Serialize Object");
            ByteArrayOutputStream bout = NetObjectOutputStream.convertObjectToBytes(result, compressed, encrypted);
//            ThreadTracer.getInstance().updateEvent("end Serialize Object");
            if (Profiler.log.isDebugEnabled()) {
                Profiler.log.debug(svc + " serilaize |costtime=" + (System.currentTimeMillis() - now) + "|");
            }

            now = System.currentTimeMillis();
            int lSize = bout.size();
//            ThreadTracer.getInstance().beginWriteToClient(lSize);
            NetObjectOutputStream.writeInt(response.getOutputStream(), lSize);
            bout.writeTo(response.getOutputStream());
            response.getOutputStream().flush();
//            ThreadTracer.getInstance().endWriteToClient(lSize);
            if (Profiler.log.isDebugEnabled()) {
                Profiler.log.debug(svc + " |flowsize=" + lSize + "| write net |costtime=" + (System.currentTimeMillis() - now) + "|");
            }
        } catch (Throwable var13) {
            if (inited) {
                Logger.error("When send the result to client error occured", var13);
            } else {
                this.log.error("When send the result to client error occured", var13);
            }
        }

        response.flushBuffer();
    }

    private void postRemoteProcess() {
        if (this.factory != null) {
            this.factory.postProcess();
        }

    }

    private void postErrorRemoteProcess(Throwable cause) {
        if (this.factory != null) {
            this.factory.postErrorProcess(cause);
        }

    }

    private void preRemoteProcess() {
        if (this.factory != null) {
            this.factory.preProcess();
        }

    }

    public Object invokeBeanMethod(String module, String beanName, String methodName, Class[] parameterTypes, Object[] beanParameters) throws Throwable {
        if (Profiler.log.isDebugEnabled()) {
            Profiler.log.debug(enterMethodMsgFormat.format(new Object[]{beanName, methodName}));
        }

        long nowTime = System.currentTimeMillis();
        Object o = null;
        boolean var21 = false;

        Object var13;
        try {
            try {
                var21 = true;
                if (module == null) {
                    o = this.remoteCtx.lookup(beanName);
                } else {
                    Context moduleCtx = (Context)this.ctxMap.get(module);
                    if (moduleCtx == null) {
                        Properties props = new Properties();
                        props.put("nc.targetModule", module);
                        props.put("nc.locator.provider", "nc.bs.framework.server.ModuleNCLocator");
                        moduleCtx = NCLocator.getInstance(props);
                        this.ctxMap.put(module, moduleCtx);
                    }

                    o = ((Context)moduleCtx).lookup(beanName);
                }
            } catch (ComponentException var22) {
                Logger.error("component lookup error", var22);
                throw var22;
            }

            Method bm = o.getClass().getMethod(methodName, parameterTypes);
            bm.setAccessible(true);
            if (bm == null) {
            }

            Object result = bm.invoke(o, beanParameters);
            var13 = result;
            var21 = false;
        } finally {
            if (var21) {
                long invokeTakeTime = System.currentTimeMillis() - nowTime;
                if (Profiler.log.isDebugEnabled()) {
                    Profiler.log.debug(leaveMethodMsgFormat.format(new Object[]{beanName, methodName, invokeTakeTime}));
                }

            }
        }

        long invokeTakeTime = System.currentTimeMillis() - nowTime;
        if (Profiler.log.isDebugEnabled()) {
            Profiler.log.debug(leaveMethodMsgFormat.format(new Object[]{beanName, methodName, invokeTakeTime}));
        }

        return var13;
    }

    private Throwable warpThrowable(String msg, Throwable thr) {
        if (verifyThrowable(thr)) {
            return thr;
        } else {
            Logger.error("Call Service error, which will wrapped to client", thr);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            thr.printStackTrace(pw);
            return new FrameworkRuntimeException(msg + ":" + sw.getBuffer());
        }
    }

    private static boolean verifyThrowable(Throwable thr) {
        if (thr == null) {
            return true;
        } else {
            String pkgName = thr.getClass().getPackage().getName();
            return !pkgName.startsWith("com.ibm.") && !pkgName.startsWith("weblogic.") && (!pkgName.startsWith("javax.") || pkgName.startsWith("javax.xml")) ? verifyThrowable(thr.getCause()) : false;
        }
    }

    private void traceForMonitor(InvocationInfo info) {
        String remoteCallmethod = info.getServiceName() + "." + info.getMethodName();
        String remoteAddr = info.getRemoteHost() + "." + info.getRemotePort();
//        ThreadTracer.getInstance().startThreadMonitor(remoteCallmethod, remoteAddr, info.getUserCode());
        String ds = null;
        ds = info.getUserDataSource();
        if (ds == null) {
            ds = "design";
        }

        Logger.putMDC("datasource", ds);
        Logger.putMDC("user", info.getUserCode());
    }

    private static Throwable extractException(Throwable exp) {
        return exp instanceof InvocationTargetException ? extractException(exp.getCause()) : exp;
    }

    public static Object readObject(InputStream in, boolean[] retValue, int[] lsizes) throws IOException, ClassNotFoundException {
        BufferedInputStream bin = new BufferedInputStream(in);
        int len = NetObjectInputStream.readInt(bin);
        byte[] bytes = new byte[len];
        int readLen = bin.read(bytes);

        int tmpLen;
        for(lsizes[0] = readLen; readLen < len; readLen += tmpLen) {
            tmpLen = bin.read(bytes, readLen, len - readLen);
            if (tmpLen < 0) {
                break;
            }
        }

        if (readLen < len) {
            throw new EOFException("ReadObject EOF error readLen: " + readLen + " expected: " + len);
        } else {
            NetObjectInputStream objIn = new NetObjectInputStream(new ByteArrayInputStream(bytes));
            if (retValue != null) {
                retValue[0] = objIn.isCompressed();
                retValue[1] = objIn.isEncrypted();
            }

            return objIn.readObject();
        }
    }
}
