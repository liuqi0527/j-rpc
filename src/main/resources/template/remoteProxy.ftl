<#include "script.ftl" parse=true encoding="utf-8">

package ${package};

import pers.liuqi.rpc.AbstractServiceRemoteProxy;
import pers.liuqi.rpc.service.ServiceInfo;
import ${interfaceClass};

<#-- 类名 -->
public final class ${className} extends AbstractRemoteServiceCaller implements ${interfaceClass} {

    public ${className}(ServiceInfo info) {
        super(info);
    }


<#-- 实现接口的每一个方法 -->
<#list methods as method>

    @Override
    public ${method.returnType.name} ${method.name}(<@formalParam paramTypes = method.parameterTypes />) {
        remoteInvoke("${method.name}" <@actualParam paramTypes = method.parameterTypes />);
    <@genReturn method.returnType />
    }
</#list>

}




