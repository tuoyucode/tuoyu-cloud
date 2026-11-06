<#-- Vben 风格：中文文案，放到前端 locales/langs/zh-CN/<模块>/<业务>.json，键为 <模块>.<业务>.* -->
{
  "title": "${functionName?json_string}",
  "list": "${functionName?json_string}列表",
  "create": "新增${functionName?json_string}",
  "edit": "编辑${functionName?json_string}",
  "delete": "删除${functionName?json_string}",
  "exportFileName": "${functionName?json_string}",
<#list columns as column>
  "${column.javaField}": "${(column.columnLabel!column.javaField)?json_string}"<#sep>,</#sep>
</#list>
}
