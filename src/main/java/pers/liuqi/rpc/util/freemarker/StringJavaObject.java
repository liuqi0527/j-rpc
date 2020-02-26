package pers.liuqi.rpc.util.freemarker;

import javax.tools.SimpleJavaFileObject;
import java.io.IOException;
import java.net.URI;

/**
 * @author LiuQi - [Created on 2018-08-07]
 */
public class StringJavaObject extends SimpleJavaFileObject {

    /**
     * 源代码
     */
    private String classSrc;

    /**
     * 遵循Java规范的类名及文件
     *
     * @param className
     * @param classSrc
     */
    public StringJavaObject(String className, String classSrc) {
        super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
        this.classSrc = classSrc;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        return classSrc;
    }

}

