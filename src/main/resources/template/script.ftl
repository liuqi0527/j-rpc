<#-- 形参列表 -->
<#macro formalParam paramTypes>
    <#list paramTypes as typeClass>${typeClass.name} var${typeClass?index}<#sep>,</#list>
</#macro>

<#-- 实参列表 -->
<#macro actualParam paramTypes>
    <#if (paramTypes?size > 0)>
        ,
    </#if>
    <#list paramTypes as typeClass>var${typeClass?index}<#sep>,</#list>
</#macro>

<#-- 实参列表 -->
<#macro genReturn returnType>
    <#if returnType == "int" || returnType == "long" || returnType == "short" || returnType == "double" || returnType == "float" || returnType == "byte">
        return 0;
    <#elseif returnType == "boolean">
        return false;
    <#elseif returnType != "void">
        return null;
    </#if>
</#macro>


