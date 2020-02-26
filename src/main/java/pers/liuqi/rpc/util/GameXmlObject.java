package pers.liuqi.rpc.util;

import com.egls.server.utils.databind.xml.XmlObject;
import com.egls.server.utils.exception.ExceptionUtil;
import com.egls.server.utils.text.XmlUtil;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.w3c.dom.Element;

import java.io.File;

/**
 * <pre>
 * 实现此接口的类代表一个Xml结构的节点，需要映射到子节点的属性需要按需添加合适
 * 的注解配置框架会根据属性注解的配置来自动将通过Xml配置的数据映射到Java对象中。
 * 接口实现类必须有一个无参构造函数，属性通过反射方式设置无需setter方法
 * </pre>
 *
 * @author LiuQi - [Created on 2020-02-12]
 */
public interface GameXmlObject extends XmlObject {

    @Override
    default void load(final File file) {
        try {
            Element[] rootElements = XmlUtil.getChildren(XmlUtil.loadDocumentElement(file), getRootElementName());
            if (ArrayUtils.isEmpty(rootElements)) {
                throw new RuntimeException(String.format("not exist any %s element.", getRootElementName()));
            }
            if (rootElements.length != 1) {
                throw new RuntimeException(String.format("root %s element is duplicate.", getRootElementName()));
            }
            load(rootElements[0]);
        } catch (Exception exception) {
            throw new RuntimeException(String.format("[file:%s] -> %s", FilenameUtils.normalize(file.getAbsolutePath()), exception.getMessage()), ExceptionUtil.getSuperCause(exception));
        }
    }

    default String getRootElementName() {
        return "game";
    }

}
