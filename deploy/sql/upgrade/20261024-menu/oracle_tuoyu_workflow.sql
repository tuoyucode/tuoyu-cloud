-- 2026-10-24 删除请假申请示例（tuoyu-workflow 库）。可重复执行。
begin
  execute immediate 'drop table test_leave';
exception
  when others then
    if sqlcode != -942 then
      raise;
    end if;
end;
/
