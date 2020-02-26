package pers.liuqi.rpc.freemarker;


import com.egls.server.utils.file.FileUtil;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ConfigurationBuilder;
import pers.liuqi.rpc.AbstractServiceProvider;
import pers.liuqi.rpc.AbstractServiceRemoteProxy;
import pers.liuqi.rpc.config.Constant;
import pers.liuqi.rpc.exception.ServiceImplementNotFoundException;
import pers.liuqi.rpc.service.Service;
import pers.liuqi.rpc.service.ServiceInfo;
import pers.liuqi.rpc.util.RuntimeLogger;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

/**
 * @author LiuQi - [Created on 2018-08-03]
 */
public class DynamicClassUtil {

    private static volatile freemarker.template.Configuration templateConfig;

    private static volatile Reflections reflections = null;

    private static String basePath = "D:/workspace/server/refactor/rpc/dClass";

    static {
        try {
            FileUtil.deleteFile(basePath);
            FileUtil.createDirOnNoExists(basePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static AbstractServiceProvider buildServiceInvoker(Class<?> interfaceClass) throws IllegalAccessException,
            InstantiationException, TemplateException, IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, URISyntaxException {

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
        //代理类名称
        String dynamicProxyClassName = "Invoker$$" + serviceClass.getSimpleName();

        //模板变量
        Map<String, Object> params = new HashMap<>(3);
        params.put("className", dynamicProxyClassName);
        params.put("methods", interfaceClass.getMethods());
        params.put("serviceClass", service.getClass().getName());

        //根据模板变量生成代理类
        Class dynamicInvokerClass = DynamicClassUtil.buildClass(Constant.TEMPLATE_INVOKER, dynamicProxyClassName, params);
        Constructor constructor = dynamicInvokerClass.getConstructor(serviceClass);
        return (AbstractServiceProvider) constructor.newInstance(service);
    }


    public static Object buildLocalCaller(Class<?> interfaceClass, AbstractServiceProvider invoker, ServiceInfo info) throws TemplateException,
            IOException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException, URISyntaxException {

        //代理类名称
        String dynamicProxyClassName = "LocalCaller$$" + interfaceClass.getSimpleName();

        //模板变量
        Map<String, Object> params = new HashMap<>(3);
        params.put("className", dynamicProxyClassName);
        params.put("methods", interfaceClass.getMethods());
        params.put("interfaceClass", interfaceClass.getName());


        //根据模板变量生成代理类
        Class dynamicInvokerClass = DynamicClassUtil.buildClass(Constant.TEMPLATE_LOCAL_CALLER, dynamicProxyClassName, params);
        Constructor constructor = dynamicInvokerClass.getConstructor(AbstractServiceProvider.class, ServiceInfo.class);
        return constructor.newInstance(invoker, info);
    }

    public static AbstractServiceRemoteProxy buildRemoteCaller(ServiceInfo info) throws ClassNotFoundException,
            IOException, TemplateException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException, URISyntaxException {

        //服务接口类
        Class<?> interfaceClass = Class.forName(info.getServiceInterfaceName());

        //代理类名称
        String dynamicProxyClassName = "RemoteCaller$$" + info.getName() + "$$" + info.getId();

        //模板变量
        Map<String, Object> params = new HashMap<>(3);
        params.put("className", dynamicProxyClassName);
        params.put("methods", interfaceClass.getMethods());
        params.put("interfaceClass", interfaceClass.getName());


        //根据模板变量生成代理类
        Class dynamicInvokerClass = DynamicClassUtil.buildClass(Constant.TEMPLATE_REMOTE_CALLER, dynamicProxyClassName, params);
        Constructor constructor = dynamicInvokerClass.getConstructor(ServiceInfo.class);
        return (AbstractServiceRemoteProxy) constructor.newInstance(info);
    }

    private static Class buildClass(String templateName, String sampleClassName, Map<String, Object> params) throws
            IOException, TemplateException, ClassNotFoundException, URISyntaxException {

        //类全名，所属的包名
        String className = Constant.DYNAMIC_CLASS_PACKAGE + "." + sampleClassName;
        params.put("package", Constant.DYNAMIC_CLASS_PACKAGE);


        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            RuntimeLogger.error("class not found", className);
        }

        //查找类路径、删除旧的类文件，编译形成新的类文件
        URL resource = Thread.currentThread().getContextClassLoader().getResource("");
        System.out.println("res : " + resource);
        File classPath = new File(resource.toURI());
        String outDir = classPath.getAbsolutePath() + File.separator;
        String classFile = outDir + Constant.DYNAMIC_CLASS_PACKAGE.replace('.', File.separatorChar) + File.separatorChar + sampleClassName + ".class";
        RuntimeLogger.info("temp dir : " + outDir);
        FileUtil.deleteFile(classFile);

        try {
            RuntimeLogger.info("find class " + className);

            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            RuntimeLogger.info("cant find class " + className + " begin build");

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                //取到代理类的模板
                Template template = getTemplate(templateName);
                //通过模板生成java源代码
                template.process(params, new OutputStreamWriter(outputStream));
                debugPrintClass(sampleClassName, outputStream.toByteArray());

                //编译参数
                List<String> options = new ArrayList<>();
                options.add("-target");
                options.add("1.8");
                options.add("-d");
                options.add(basePath);

                //Java 文件对象
                JavaFileObject javaObject = new StringJavaObject(className, outputStream.toString());

                //设置编译环境
                JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
                StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
                JavaCompiler.CompilationTask compilerTask = compiler.getTask(null, fileManager, null, options, null, Collections.singletonList(javaObject));
                //编译，获取对应的Class对象
                if (compilerTask.call()) {
                    ClassPool pool = ClassPool.getDefault();
                    FileInputStream inputStream = new FileInputStream(new File(basePath + "/" + className.replaceAll("\\.", "/") + ".class"));
                    CtClass ctClass = pool.makeClass(inputStream);
                    return ctClass.toClass();
                }
            } catch (CannotCompileException e1) {
                e1.printStackTrace();
            }
        }
        return null;
    }

    private static void debugPrintClass(String className, byte[] bytes) {
        File file = new File("./dynamic/" + className + ".java");
        try {
            FileUtil.createFileOnNoExists(file);
            FileUtil.write(file, bytes);
        } catch (IOException e) {
            RuntimeLogger.error("", e);
        }
    }

    private static Reflections getReflection() {
        if (reflections == null) {
            ConfigurationBuilder configuration = new ConfigurationBuilder();
            configuration.addScanners(new SubTypesScanner());
            configuration.forPackages("pers.liuqi");
            configuration.filterInputsBy(fileName -> StringUtils.contains(fileName, ".class"));

            reflections = new Reflections(configuration);
        }
        return reflections;
    }

    private static Template getTemplate(String templateName) throws IOException {
        if (templateConfig == null) {
            templateConfig = new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_22);
            templateConfig.setClassForTemplateLoading(DynamicClassUtil.class, "/template");
            templateConfig.setDefaultEncoding("UTF-8");
            templateConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        }

        return templateConfig.getTemplate(templateName);
    }

}
