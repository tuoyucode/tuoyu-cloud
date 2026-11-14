package org.tuoyu.common.mybatis.interceptor;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.parser.JsqlParserSupport;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mybatis.handler.DataPermissionSqlHandler;
import org.tuoyu.common.mybatis.helper.DataPermissionHelper;

import java.sql.Connection;

/**
 * 数据权限拦截器（MyBatis-Plus 内部拦截器）：调用标注了 {@code @DataPermission} 的方法期间执行的 SQL，
 * 按 {@link DataPermissionSqlHandler} 生成的条件改写最外层的 WHERE。
 * <p>
 * 改写的时机与范围：
 * <ul>
 *     <li>查询在 {@code beforeQuery} 中改写，修改与删除在 {@code beforePrepare} 中改写，插入不改写；</li>
 *     <li>没有当前注解、处于忽略数据权限的代码块中、或 Mapper / 方法标注了
 *     {@code @InterceptorIgnore(dataPermission = "true")} 时不改写；</li>
 *     <li>查询的条件加在最外层的 WHERE：UNION 等集合查询的每个分支、带括号的查询（可以多层）的括号内都加；
 *     {@code WITH} 子句、{@code FROM} 与 WHERE 中的子查询、联表的 {@code ON}、查询列中的子查询不改写。
 *     无法识别的查询类型拒绝执行，不会不加条件放行。</li>
 * </ul>
 * 与其他拦截器的关系：
 * <ul>
 *     <li>拦截器链的顺序由 {@code MybatisPlusConfiguration} 决定：租户 → 数据权限 → 分页 → 乐观锁。MyBatis-Plus
 *     对查询按顺序对每个内部拦截器执行 {@code willDoQuery} 再执行 {@code beforeQuery}，分页插件在 {@code willDoQuery}
 *     中用当时的 SQL 统计总数，所以统计语句也带数据权限条件；</li>
 *     <li>租户条件由租户插件加（包括子查询与联表），与数据权限条件互不影响；</li>
 *     <li>游标查询（{@code Executor.queryCursor}）不经过 MyBatis-Plus 的查询拦截，不受数据权限与租户限制，项目中没有使用游标；</li>
 *     <li>完整 SQL 日志（{@link SqlLogInterceptor}）在语句执行时输出，看到的是改写后的 SQL。</li>
 * </ul>
 *
 * @author tuoyu
 */
public class DataPermissionSqlInterceptor extends JsqlParserSupport implements InnerInterceptor {

    private final DataPermissionSqlHandler handler;

    /**
     * @param handler 生成数据权限条件
     */
    public DataPermissionSqlInterceptor(DataPermissionSqlHandler handler) {
        this.handler = handler;
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds,
                            ResultHandler resultHandler, BoundSql boundSql) {
        if (skip(ms)) {
            return;
        }
        PluginUtils.MPBoundSql mpBoundSql = PluginUtils.mpBoundSql(boundSql);
        mpBoundSql.sql(parserSingle(mpBoundSql.sql(), ms.getId()));
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        PluginUtils.MPStatementHandler mpStatementHandler = PluginUtils.mpStatementHandler(sh);
        MappedStatement ms = mpStatementHandler.mappedStatement();
        SqlCommandType type = ms.getSqlCommandType();
        if (type != SqlCommandType.UPDATE && type != SqlCommandType.DELETE) {
            return;
        }
        if (skip(ms)) {
            return;
        }
        PluginUtils.MPBoundSql mpBoundSql = mpStatementHandler.mPBoundSql();
        mpBoundSql.sql(parserMulti(mpBoundSql.sql(), ms.getId()));
    }

    /**
     * @return 是否不改写：语句处于忽略数据权限的状态，或当前没有数据权限注解
     */
    private static boolean skip(MappedStatement ms) {
        return InterceptorIgnoreHelper.willIgnoreDataPermission(ms.getId()) || DataPermissionHelper.getPermission() == null;
    }

    @Override
    protected void processSelect(Select select, int index, String sql, Object obj) {
        restrict(select);
    }

    /**
     * 给查询的最外层（集合查询的每个分支、括号内的查询）加上条件
     */
    private void restrict(Select select) {
        if (select instanceof PlainSelect plainSelect) {
            Expression where = handler.getSqlSegment(plainSelect.getWhere(), true);
            if (where != null) {
                plainSelect.setWhere(where);
            }
        } else if (select instanceof SetOperationList setOperationList) {
            for (Select branch : setOperationList.getSelects()) {
                restrict(branch);
            }
        } else if (select instanceof ParenthesedSelect parenthesedSelect) {
            restrict(parenthesedSelect.getSelect());
        } else {
            // 例如 VALUES：无法加条件，拒绝执行
            throw new ServiceException("数据权限不支持该查询语句");
        }
    }

    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        Expression where = handler.getSqlSegment(update.getWhere(), false);
        if (where != null) {
            update.setWhere(where);
        }
    }

    @Override
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        Expression where = handler.getSqlSegment(delete.getWhere(), false);
        if (where != null) {
            delete.setWhere(where);
        }
    }

}
