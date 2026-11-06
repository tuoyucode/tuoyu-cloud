<#-- Vben 风格：英文文案，由字段名生成，请按业务含义修改 -->
<#assign title = className?replace("([A-Z])", " $1", "r")?lower_case?cap_first>
{
  "title": "${title}",
  "list": "${title} list",
  "create": "New ${title?lower_case}",
  "edit": "Edit ${title?lower_case}",
  "delete": "Delete ${title?lower_case}",
  "exportFileName": "${businessName}",
<#list columns as column>
  "${column.javaField}": "${column.javaField?replace("([A-Z])", " $1", "r")?lower_case?cap_first}"<#sep>,</#sep>
</#list>
}
