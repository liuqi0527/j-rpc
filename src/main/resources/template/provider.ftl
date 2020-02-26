<#include "script.ftl" parse=true encoding="utf-8">

package ${package};

import pers.liuqi.rpc.AbstractServiceProvider;
import pers.liuqi.rpc.service.Service;
import pers.liuqi.rpc.invoke.Invoke;

<#-- 类名 -->
public final class ${className} extends AbstractServiceProvider {

    private ${serviceClass} service;

    public ${className}(${serviceClass} service) {
        super(service.getTickInterval());
        this.service = service;
    }

    @Override
    public Service getService() {
        return this.service;
    }

    @Override
    public Object invoke(Invoke invoke) {
        Object[] params = invoke.getParams();
        switch(invoke.getMethodName()) {

<#list methods as method>
            case "${method.name}":
    <#if method.returnType.name == "void">
                service.${method.name}(<#list method.parameterTypes as paramType>(${paramType.name})params[${paramType?index}]<#sep> ,</#list>);
                return null;
    <#else>
                return service.${method.name}(<#list method.parameterTypes as paramType>(${paramType.name})params[${paramType?index}]<#sep> ,</#list>);
    </#if>
</#list>
            default:
                return null;
        }
    }
}