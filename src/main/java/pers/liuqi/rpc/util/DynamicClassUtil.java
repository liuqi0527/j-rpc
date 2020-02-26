package pers.liuqi.rpc.util;

import pers.liuqi.rpc.AbstractServiceProvider;
import pers.liuqi.rpc.AbstractServiceRemoteProxy;
import pers.liuqi.rpc.RpcContext;
import pers.liuqi.rpc.util.RuntimeLogger;
import pers.liuqi.rpc.config.Constant;
import pers.liuqi.rpc.exception.ServiceImplementNotFoundException;
import pers.liuqi.rpc.invoke.Invoke;
import pers.liuqi.rpc.service.Service;
import pers.liuqi.rpc.service.ServiceInfo;
import javassist.*;
import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author LiuQi - [Created on 2018-08-07]
 */
public class DynamicClassUtil {
    //@formatter:off

    private static final String PREFIX_PROVIDER      = Constant.DYNAMIC_CLASS_PACKAGE + ".Provider$$";
    private static final String PREFIX_LOCAL_PROXY   = Constant.DYNAMIC_CLASS_PACKAGE + ".LocalProxy$$";
    private static final String PREFIX_REMOTE_PROXY  = Constant.DYNAMIC_CLASS_PACKAGE + ".RemoteProxy$$";
    private static final String VOID_TYPE            = "void";

    private static final Map<String, List<String>> PRIMARY_TYPE_MAP = new HashMap<>();

    private static volatile Reflections reflections = null;

    static {
        //基础数据类型，包装类，拆箱方法，默认值
        PRIMARY_TYPE_MAP.put("int",     Arrays.asList("Integer",   "intValue"     , "0"));
        PRIMARY_TYPE_MAP.put("long",    Arrays.asList("Long",      "longValue"    , "0"));
        PRIMARY_TYPE_MAP.put("short",   Arrays.asList("Short",     "shortValue"   , "0"));
        PRIMARY_TYPE_MAP.put("double",  Arrays.asList("Double",    "doubleValue"  , "0"));
        PRIMARY_TYPE_MAP.put("float",   Arrays.asList("Float",     "floatValue"   , "0"));
        PRIMARY_TYPE_MAP.put("byte",    Arrays.asList("Byte",      "byteValue"    , "0"));
        PRIMARY_TYPE_MAP.put("char",    Arrays.asList("Character", "charValue"    , "0"));
        PRIMARY_TYPE_MAP.put("boolean", Arrays.asList("Boolean",   "booleanValue" , "false"));
    }
    //@formatter:on


    private static Reflections getReflection() {
        if (reflections == null) {
            ConfigurationBuilder configuration = new ConfigurationBuilder();
            configuration.addScanners(new SubTypesScanner());
            configuration.addUrls(ClasspathHelper.forPackage(RpcContext.class.getPackage().getName()));
            reflections = new Reflections(configuration);
        }
        return reflections;
    }

    private static ClassPool getClassPool() {
        ClassPool classPool = ClassPool.getDefault();
        classPool.importPackage(RpcContext.class.getPackage().getName());
        classPool.importPackage(Invoke.class.getPackage().getName());
        classPool.importPackage(Service.class.getPackage().getName());
        return classPool;
    }

    private static Class<?> getDynamicClass(String className, ClassPool classPool) throws CannotCompileException {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static AbstractServiceProvider buildServiceProvider(Class<?> interfaceClass) throws IllegalAccessException,
            InstantiationException, NotFoundException, CannotCompileException, NoSuchMethodException, InvocationTargetException {

        //查找实现指定接口的服务类
        Class<? extends Service> serviceClass = null;
        Set<Class<? extends Service>> allServiceClass = getReflection().getSubTypesOf(Service.class);
        for (Class<? extends Service> sClass : allServiceClass) {
            for (Class<?> iClass : sClass.getInterfaces()) {
                if (iClass == interfaceClass) {
                    serviceClass = sClass;
                    break;
                }
            }
        }

        //未找到任何实现类，报错
        if (serviceClass == null) {
            throw new ServiceImplementNotFoundException(interfaceClass);
        }

        //实例化服务实现类
        Service service = serviceClass.newInstance();
        //代理类
        String providerClassName = PREFIX_PROVIDER + serviceClass.getSimpleName();
        RuntimeLogger.info("build service provider", providerClassName);

        ClassPool classPool = getClassPool();
        Class dynamicClass = getDynamicClass(providerClassName, classPool);
        if (dynamicClass == null) {
            CtClass ctClass = classPool.makeClass(providerClassName);

            //父类
            ctClass.setSuperclass(classPool.get(AbstractServiceProvider.class.getName()));

            //声明成员变量
            ctClass.addField(CtField.make(String.format("private %s service;", service.getClass().getName()), ctClass));

            //构造函数
            CtConstructor ctConstructor = new CtConstructor(new CtClass[]{classPool.get(service.getClass().getName())}, ctClass);
            ctConstructor.setBody("{super($1.getTickInterval()); this.service = $1; }");
            ctClass.addConstructor(ctConstructor);

            //方法#getService()
            ctClass.addMethod(CtMethod.make("public Service getService() {return this.service;}", ctClass));

            //方法#invoke()
            StringBuilder methodBody = new StringBuilder();
            methodBody.append("public Object invoke(Invoke invoke) {");
            methodBody.append("Object[] params = invoke.getParams();");

            //通过判断方法名称，将调用路由到实际的方法执行
            for (int i = 0; i < interfaceClass.getMethods().length; i++) {
                Method method = interfaceClass.getMethods()[i];
                if (i != 0) {
                    methodBody.append("else ");
                }

                //方法名称判断
                methodBody.append(String.format("if ( \"%s\".equals(invoke.getMethodName()) ) {", method.getName()));
                //对应的service方法的调用
                String methodInvokeString = serviceMethodInvoke(method);
                if (!StringUtils.equals(method.getReturnType().getName(), VOID_TYPE)) {
                    methodBody.append("return ").append(box(method.getReturnType(), methodInvokeString)).append(";}");
                } else {
                    methodBody.append(methodInvokeString).append(";}");
                }
            }

            methodBody.append("return null;}");
            ctClass.addMethod(CtMethod.make(methodBody.toString(), ctClass));

            debugPrintClass(providerClassName, ctClass);
            dynamicClass = ctClass.toClass();
        }

        Constructor constructor = dynamicClass.getConstructor(serviceClass);
        return (AbstractServiceProvider) constructor.newInstance(service);
    }

    private static String serviceMethodInvoke(Method method) {
        /*构建service方法调用, service.say("hello");*/
        StringBuilder methodBody = new StringBuilder();
        methodBody.append("service.").append(method.getName()).append("(");
        //方法调用的参数列表
        for (int j = 0; j < method.getParameterTypes().length; j++) {
            if (j > 0) {
                methodBody.append(", ");
            }
            Class<?> paramType = method.getParameterTypes()[j];
            methodBody.append(unBox(paramType, String.format("params[%d]", j)));
        }
        methodBody.append(")");
        return methodBody.toString();
    }

    public static Object buildLocalProxy(Class<?> interfaceClass, AbstractServiceProvider invoker, ServiceInfo info) throws IllegalAccessException,
            InstantiationException, NotFoundException, CannotCompileException, NoSuchMethodException, InvocationTargetException {

        //代理类
        String proxyClassName = PREFIX_LOCAL_PROXY + interfaceClass.getSimpleName();
        RuntimeLogger.info("build local service proxy", proxyClassName);

        ClassPool classPool = getClassPool();
        Class dynamicClass = getDynamicClass(proxyClassName, classPool);
        if (dynamicClass == null) {
            CtClass ctClass = classPool.makeClass(proxyClassName);
            //实现类
            ctClass.addInterface(classPool.get(interfaceClass.getName()));


            //声明成员变量
            ctClass.addField(CtField.make("private AbstractServiceProvider invoker;", ctClass));
            ctClass.addField(CtField.make("private ServiceInfo info;", ctClass));

            //构造函数
            CtConstructor ctConstructor = new CtConstructor(new CtClass[]{classPool.get(AbstractServiceProvider.class.getName()), classPool.get(ServiceInfo.class.getName())}, ctClass);
            ctConstructor.setBody(String.format("{ this.invoker = (%s) $1; this.info = (%s) $2; }", AbstractServiceProvider.class.getName(), ServiceInfo.class.getName()));
            ctClass.addConstructor(ctConstructor);


            //接口实现
            for (Method method : interfaceClass.getMethods()) {
                StringBuilder methodBody = new StringBuilder();

                //方法签名
                fillMethodSign(methodBody, method);
                //封装invoke
                methodBody.append("Invoke invoke = RpcContext.buildInvoke");
                fillMethodParams(methodBody, "this.info.getName()", method);

                //调用本地invoker
                methodBody.append("invoker.localInvoke(invoke);");

                //返回值
                methodBody.append(returnDefaultValue(method.getReturnType()));
                methodBody.append("}");
                ctClass.addMethod(CtMethod.make(methodBody.toString(), ctClass));
            }

            debugPrintClass(proxyClassName, ctClass);
            dynamicClass = ctClass.toClass();
        }

        Constructor constructor = dynamicClass.getConstructor(AbstractServiceProvider.class, ServiceInfo.class);
        return constructor.newInstance(invoker, info);
    }

    public static AbstractServiceRemoteProxy buildRemoteProxy(ServiceInfo info) throws IllegalAccessException,
            InstantiationException, NotFoundException, CannotCompileException, NoSuchMethodException, InvocationTargetException, ClassNotFoundException {

        Class<?> interfaceClass = Class.forName(info.getServiceInterfaceName());
        //代理类
        String proxyClassName = PREFIX_REMOTE_PROXY + info.getName() + "$$" + info.getId();
        RuntimeLogger.info("build remote service proxy", proxyClassName);

        ClassPool classPool = getClassPool();
        Class dynamicClass = getDynamicClass(proxyClassName, classPool);
        if (dynamicClass == null) {
            CtClass ctClass = classPool.makeClass(proxyClassName);
            //接口
            ctClass.addInterface(classPool.get(interfaceClass.getName()));
            //父类
            ctClass.setSuperclass(classPool.get(AbstractServiceRemoteProxy.class.getName()));


            //构造函数
            CtConstructor ctConstructor = new CtConstructor(new CtClass[]{classPool.get(ServiceInfo.class.getName())}, ctClass);
            ctConstructor.setBody("{ super($1); }");
            ctClass.addConstructor(ctConstructor);

            //接口实现
            for (Method method : interfaceClass.getMethods()) {
                StringBuilder methodBody = new StringBuilder();

                //方法签名
                fillMethodSign(methodBody, method);
                //调用父类方法
                methodBody.append("remoteInvoke");
                fillMethodParams(methodBody, null, method);

                //返回值
                methodBody.append(returnDefaultValue(method.getReturnType()));
                methodBody.append("}");

                //增加方法
                ctClass.addMethod(CtMethod.make(methodBody.toString(), ctClass));
            }

            debugPrintClass(proxyClassName, ctClass);
            dynamicClass = ctClass.toClass();
        }

        Constructor constructor = dynamicClass.getConstructor(ServiceInfo.class);
        return (AbstractServiceRemoteProxy) constructor.newInstance(info);
    }

    /**
     * 方法签名
     *
     * @param methodBody
     * @param method
     */
    private static void fillMethodSign(StringBuilder methodBody, Method method) {
        //public java.util.Set getFriendList(java.lang.String var0) {
        methodBody.append("public ").append(method.getReturnType().getName()).append(" ").append(method.getName()).append("(");
        //参数列表
        for (int i = 0; i < method.getParameterTypes().length; i++) {
            if (i > 0) {
                methodBody.append(", ");
            }
            Class<?> typeClass = method.getParameterTypes()[i];
            methodBody.append(typeClass.getName()).append(" var").append(i);
        }
        methodBody.append("){");
    }

    /**
     * 组装方法名字，参数列表供方法调用使用
     *
     * @param methodBody
     * @param method
     */
    private static void fillMethodParams(StringBuilder methodBody, String extra, Method method) {
        //"getFriendList", var0

        methodBody.append("(");
        if (extra != null) {
            methodBody.append(extra).append(",");
        }
        //方法名称
        methodBody.append("\"").append(method.getName()).append("\"");
        methodBody.append(", ");
        if (method.getParameterTypes().length > 0) {
            methodBody.append("new Object[]{");
            //参数列表
            for (int i = 0; i < method.getParameterTypes().length; i++) {
                if (i > 0) {
                    methodBody.append(", ");
                }

                Class<?> typeClass = method.getParameterTypes()[i];
                methodBody.append(box(typeClass, "var" + i));
            }
            methodBody.append("}");
        } else {
            methodBody.append("new Object[0]");
        }
        methodBody.append(");");
    }

    private static String returnDefaultValue(Class<?> typeClass) {
        //返回默认值
        List<String> list = PRIMARY_TYPE_MAP.get(typeClass.getName());
        if (list != null) {
            return "return " + list.get(2) + ";";
        } else if (!StringUtils.equals(typeClass.getName(), VOID_TYPE)) {
            return "return null;";
        }
        return "";
    }


    /**
     * 拆箱
     * 由于javassist生成的类没有自动装箱、拆箱功能，所有需要特殊处理
     *
     * @param typeClass
     * @param typeName
     * @return
     */
    private static String unBox(Class<?> typeClass, String typeName) {
        //接受的参数是基础数据类型，先转成对应的包装类，再通过包装类的方法转成基础类型
        List<String> list = PRIMARY_TYPE_MAP.get(typeClass.getName());
        if (list != null) {
            return String.format("((%s) %s).%s()", list.get(0), typeName, list.get(1));
        }

        //普通类型，直接强制类型转换
        return String.format("(%s) %s", typeClass.getName(), typeName);
    }

    /**
     * 装箱
     * 由于javassist生成的类没有自动装箱、拆箱功能，所有需要特殊处理
     *
     * @param typeClass
     * @param typeName
     * @return
     */
    private static String box(Class<?> typeClass, String typeName) {
        //接受的参数是基础数据类型，直接调用对应类型的valueOf方法拆箱
        List<String> list = PRIMARY_TYPE_MAP.get(typeClass.getName());
        if (list != null) {
            return String.format("%s.valueOf(%s)", list.get(0), typeName);
        }
        return typeName;
    }

    private static void debugPrintClass(String className, CtClass ctClass) {
        ctClass.debugWriteFile("./dynamic/");
    }
}