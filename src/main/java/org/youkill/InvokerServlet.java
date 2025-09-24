package org.youkill;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//


import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.GenericServlet;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import nc.bs.framework.adaptor.IHttpServletAdaptor;
import nc.bs.framework.common.NCLocator;
import nc.bs.framework.component.RemoteProcessComponetFactory;
import nc.bs.framework.exception.ComponentException;
import nc.bs.framework.exception.FrameworkRuntimeException;
import nc.bs.framework.server.BusinessAppServer;
import nc.bs.framework.server.DeployedModule;
import nc.bs.framework.server.Module;
import nc.bs.logging.Log;
import nc.bs.logging.Logger;

public class InvokerServlet extends HttpServlet {
    private static final long serialVersionUID = 1176638570930667532L;
    private static final Log log = Log.getInstance(nc.bs.framework.server.InvokerServlet.class);
    private static final String MODULE_PREFIX = "/~";
    private static final MessageFormat instantiateMsgFormat = new MessageFormat("Instantiate object error, module: {0} , service: {1}");
    private static final MessageFormat svcNotFoundMsgFormat = new MessageFormat("Service: {0} is not found in the server");
    private static Map<String, Object> serviceObjMap = Collections.synchronizedMap(new HashMap());
    private RemoteProcessComponetFactory factory = null;

    public InvokerServlet() {
    }

    public void init() {
        serviceObjMap.clear();

        try {
            this.factory = (RemoteProcessComponetFactory)NCLocator.getInstance().lookup("RemoteProcessComponetFactory");
        } catch (Throwable var2) {
            log.warn("RemoteCallPostProcess is not found");
        }

    }

    public void destroy() {
        serviceObjMap.clear();
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        this.doAction(request, response);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        this.doAction(request, response);
    }

    private void doAction(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        log.debug("Before Invoke: " + pathInfo);
        long requestTime = System.currentTimeMillis();

        try {
            if (pathInfo == null) {
                throw new ServletException("Service name is not specified, pathInfo is null");
            }

            pathInfo = pathInfo.trim();
            String moduleName = null;
            String serviceName = null;
            int beginIndex;
            if (pathInfo.startsWith("/~")) {
                moduleName = pathInfo.substring(2);
                beginIndex = moduleName.indexOf("/");
                if (beginIndex >= 0) {
                    serviceName = moduleName.substring(beginIndex);
                    if (beginIndex > 0) {
                        moduleName = moduleName.substring(0, beginIndex);
                    } else {
                        moduleName = null;
                    }
                } else {
                    moduleName = null;
                    serviceName = pathInfo;
                }
            } else {
                serviceName = pathInfo;
            }

            if (serviceName == null) {
                throw new ServletException("Service name is not specified");
            }

            beginIndex = serviceName.indexOf("/");
            if (beginIndex < 0 || beginIndex >= serviceName.length() - 1) {
                throw new ServletException("Service name is not specified");
            }

            serviceName = serviceName.substring(beginIndex + 1);
            Object obj = null;

            String method;
            try {
                obj = this.getServiceObject(moduleName, serviceName);
            } catch (ComponentException var74) {
                method = svcNotFoundMsgFormat.format(new Object[]{serviceName});
                Logger.error(method, var74);
                throw new ServletException(method);
            }

            if (obj instanceof Servlet) {
                Logger.init(obj.getClass());

                try {
                    if (obj instanceof GenericServlet) {
                        ((GenericServlet)obj).init();
                    }

                    this.preRemoteProcess();
                    ((Servlet)obj).service(request, response);
                    this.postRemoteProcess();
                } catch (ServletException var70) {
                    this.postErrorRemoteProcess(var70);
                    Logger.error("Invoker serlet: " + obj.getClass() + " error", var70);
                    throw var70;
                } catch (IOException var71) {
                    this.postErrorRemoteProcess(var71);
                    Logger.error("Invoker serlet: " + obj.getClass() + " error", var71);
                    throw var71;
                } catch (Throwable var72) {
                    this.postErrorRemoteProcess(var72);
                    Logger.error("Invoker serlet: " + obj.getClass() + " error", var72);
                    throw new ServletException("Invoker serlet: " + obj.getClass() + " error", var72);
                } finally {
                    Logger.reset();
                }
            } else if (obj instanceof IHttpServletAdaptor) {
                IHttpServletAdaptor adaptor = (IHttpServletAdaptor)obj;
                Logger.init(obj.getClass());

                try {
                    this.preRemoteProcess();
                    adaptor.doAction(request, response);
                    this.postRemoteProcess();
                } finally {
                    Logger.reset();
                }
            } else {
                if (obj == null) {
                    String msg = "Serivce: " + serviceName + " is not found";
                    log.error(msg);
                    throw new ServletException(msg);
                }

                Class clazz = obj.getClass();
                method = null;

                Method method1;

                try {
                    method1 = clazz.getDeclaredMethod("doAction", request.getClass(), response.getClass());
                } catch (Exception var68) {
                    throw new ServletException("Serivce: " + serviceName + " can't adapt Servlet");
                }

                if (method1 == null) {
                    throw new ServletException("Serivce: " + serviceName + " can't adapt Servlet");
                }

                Logger.init(obj.getClass());

                try {
                    String msg;
                    try {
                        this.preRemoteProcess();
                        method1.invoke(obj, request, response);
                        this.postRemoteProcess();
                    } catch (InvocationTargetException var75) {
                        this.postErrorRemoteProcess(var75);
                        if (var75.getTargetException() instanceof ServletException) {
                            throw (ServletException)var75.getTargetException();
                        }

                        if (var75.getTargetException() instanceof IOException) {
                            throw (IOException)var75.getTargetException();
                        }

//                        Logger.error("invoke " + obj.getClass().getName() + "." + method.getName() + " error", var75.getTargetException());
                        throw new ServletException("Servie error: " + serviceName + " " + var75.getTargetException().getMessage());
                    } catch (IllegalArgumentException var76) {
                        this.postErrorRemoteProcess(var76);
                        msg = "Serivce: " + serviceName + " invalid arguments";
                        log.error(msg);
                        throw new ServletException(msg);
                    } catch (IllegalAccessException var77) {
                        this.postErrorRemoteProcess(var77);
                        msg = "Serivce: " + serviceName + " invalid arguments";
                        log.error(msg);
                        throw new ServletException(msg);
                    } catch (Throwable var78) {
                        this.postErrorRemoteProcess(var78);
                        Logger.error("Invoker serlet: " + obj.getClass() + " error", var78);
                        throw new ServletException("Invoker serlet: " + obj.getClass() + " error", var78);
                    }
                } finally {
                    Logger.reset();
                }
            }
        } finally {
            log.debug("After Invoke: " + request.getPathInfo() + " " + (System.currentTimeMillis() - requestTime));
        }

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

    private Object getServiceObject(String moduleName, String serviceName) throws ComponentException {
        Object retObject = null;
        if (moduleName == null) {
            retObject = NCLocator.getInstance().lookup(serviceName);
        } else {
            retObject = serviceObjMap.get(moduleName + ":" + serviceName);
            if (retObject == null) {
                Module module = BusinessAppServer.getInstance().getModule(moduleName);
                if (module instanceof DeployedModule) {
                    DeployedModule deployed = (DeployedModule)module;

                    try {
                        retObject = deployed.getContext().lookup(serviceName);
                    } catch (ComponentException var11) {
                        try {
                            Class clazz = deployed.getClassLoader().loadClass(serviceName);
                            retObject = clazz.newInstance();
                        } catch (ClassNotFoundException var8) {
                            throw new FrameworkRuntimeException(instantiateMsgFormat.format(new Object[]{moduleName, serviceName}), var8);
                        } catch (InstantiationException var9) {
                            throw new FrameworkRuntimeException(instantiateMsgFormat.format(new Object[]{moduleName, serviceName}), var9);
                        } catch (IllegalAccessException var10) {
                            throw new FrameworkRuntimeException(instantiateMsgFormat.format(new Object[]{moduleName, serviceName}), var10);
                        }
                    }
                }
            }

            if (retObject != null) {
                serviceObjMap.put(moduleName + ":" + serviceName, retObject);
            }
        }

        return retObject;
    }
}

