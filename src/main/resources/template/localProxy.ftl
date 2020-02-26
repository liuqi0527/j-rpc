<#include "script.ftl" parse=true encoding="utf-8">

package ${package};

import pers.liuqi.rpc.AbstractServiceProvider;
import pers.liuqi.rpc.invoke.Invoke;
import pers.liuqi.rpc.service.ServiceInfo;
import ${interfaceClass};

<#-- 类名 -->
public final class ${className} implements ${interfaceClass} {

    private AbstractServiceProvider invoker;
    private ServiceInfo info;

    public ${className}(AbstractServiceProvider invoker, ServiceInfo info) {
        this.invoker = invoker;
        this.info = info;
    }


<#-- 实现接口的每一个方法 -->
<#list methods as method>

    @Override
    public ${method.returnType.name} ${method.name}(<@formalParam paramTypes = method.parameterTypes />) {
        Invoke invoke = RpcContext.buildInvoke(this.info.getName(), "${method.name}" <@actualParam paramTypes = method.parameterTypes />);
        invoker.localInvoke(invoke);
    <@genReturn method.returnType />
    }
</#list>

}




