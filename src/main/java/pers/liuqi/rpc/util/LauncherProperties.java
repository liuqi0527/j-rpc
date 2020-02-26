package pers.liuqi.rpc.util;

import com.egls.server.utils.CollectionUtil;
import com.egls.server.utils.file.FileNameUtil;
import com.egls.server.utils.function.StringCaster;
import com.egls.server.utils.net.ipv4.Ipv4Util;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileReader;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.function.Predicate;

/**
 * @author mayer - [Created on 2016-06-02]
 */
public class LauncherProperties {

    /**
     * 本机的IPV4地址
     */
    public static List<String> ipv4Address;


    /**
     * 本地资源的路径,一般是一些运行在本地时产生的一些文件.
     */
    public static String serverLocalPath;

    /**
     * 配置文件路径
     */
    public static String serverConfigPath;

    /**
     * HTTP资源的路径
     */
    public static String serverHttpContextPath;

    /**
     * freemarker的资源路径
     */
    public static String serverFreemarkerPath;

    /**
     * 游戏资源路径
     */
    public static String gameResourcePath;


    /**
     * 服务器的game标识,只有玩家能登陆的才会存在
     */
    public static String game;

    /**
     * 服务器的subgame标识,只有玩家能登陆的才会存在
     */
    public static String subGame;

    /**
     * TCP端口,非必有
     */
    public static int tcpPort;

    /**
     * HTTP端口,非必有
     */
    public static int httpPort;

    /**
     * 维护端口,非必有
     */
    public static int maintenancePort;

    /**
     * 配置文件的标签,必有
     */
    public static String profileLabel;

    /**
     * 是否调试模式,非必有
     */
    public static boolean debugMode;
    /**
     * 是否打印UI调试信息,非必有
     */
    public static boolean debugUiPrint;
    /**
     * 是否是否打开地图调试界面,非必有
     */
    public static boolean debugMapOpen;

    public static void load(final String serverConfigFile) throws Exception {
        //获取本机ip地址
        loadIpv4Address();
        //获取本机环境路径
        loadEnvironmentPath();
        //加载配置
        Properties properties = new Properties();
        FileReader fileReader = new FileReader(new File(serverConfigFile));
        properties.load(fileReader);
        fileReader.close();
        loadProperties(properties);
    }

    private static void loadIpv4Address() throws SocketException {
        //获取当前机器的ip地址.
        ipv4Address = Collections.unmodifiableList(Ipv4Util.getLocalhostIpv4Address(false));
        if (CollectionUtil.isEmpty(ipv4Address)) {
            throw new IllegalArgumentException("failed to get the IP address.");
        }
    }

    private static void loadEnvironmentPath() {
        File contextDir = new File(System.getProperty("user.dir"));

        String contextPath = null;

        while (contextDir.exists()) {
            File external = FileUtils.getFile(contextDir, "external");
            if (external.exists() && external.isDirectory()) {
                contextPath = contextDir.getAbsolutePath();
                break;
            }
            File parentFile = contextDir.getParentFile();
            if (parentFile == null || !parentFile.exists()) {
                break;
            }
        }

        if (contextPath == null) {
            throw new IllegalArgumentException("can not locate context from [" + new File(System.getProperty("user.dir")).getAbsolutePath() + "]");
        } else {
            contextPath = FileNameUtil.formatDirectoryPath(contextPath);
        }

        serverLocalPath = contextPath + "local" + File.separator;
        serverConfigPath = contextPath + "external" + File.separator + "config" + File.separator;
        serverHttpContextPath = contextPath + "external" + File.separator + "http" + File.separator;
        serverFreemarkerPath = contextPath + "external" + File.separator + "freemarker" + File.separator;
    }

    @SuppressWarnings("unchecked")
    private static void loadProperties(Properties properties) throws ClassNotFoundException {

    }

    private static <T> T loadProperty(Properties properties, String propertyName, Predicate<String> predicate, StringCaster<T> propertyCaster) {
        String property = StringUtils.trimToEmpty(properties.getProperty(propertyName));
        if (predicate != null && predicate.test(property)) {
            throw new IllegalArgumentException("please configure correct property [" + propertyName + "] in server.properties");
        }
        return propertyCaster.cast(property);
    }

    public static String getProfileWholeName(String fileName) {
        String part1 = StringUtils.substringBeforeLast(fileName, ".");
        String part2 = StringUtils.substringAfterLast(fileName, ".");
        return part1 + "-" + profileLabel + "." + part2;
    }

}

