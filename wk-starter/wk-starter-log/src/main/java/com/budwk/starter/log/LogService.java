package com.budwk.starter.log;

import cn.hutool.http.useragent.UserAgentUtil;
import com.alibaba.dubbo.config.annotation.Reference;
import com.budwk.starter.log.enums.LogType;
import com.budwk.starter.log.model.Sys_log;
import com.budwk.starter.log.provider.ISysLogProvider;
import com.budwk.starter.security.utils.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.nutz.aop.interceptor.async.Async;
import org.nutz.el.El;
import org.nutz.ioc.loader.annotation.Inject;
import org.nutz.ioc.loader.annotation.IocBean;
import org.nutz.json.Json;
import org.nutz.lang.Lang;
import org.nutz.lang.Strings;
import org.nutz.lang.segment.CharSegment;
import org.nutz.lang.util.Context;
import org.nutz.lang.util.MethodParamNamesScaner;
import org.nutz.mvc.Mvcs;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * @author wizzer@qq.com
 */
@IocBean
@Slf4j
public class LogService {
    @Inject
    @Reference(check = false)
    private ISysLogProvider sysLogProvider;
    private static final int STATCK_TRACE_EL_NUMBER = 2;

    public void log(LogType type, String tag, String source, CharSegment seg,
                    Map<String, El> els, boolean param, boolean result,
                    Object[] args, Object re, Method method, Object obj,
                    Throwable e, long tookTime) {
        String slogMsg;
        if (seg.hasKey()) {
            Context ctx = Lang.context();
            List<String> names = MethodParamNamesScaner.getParamNames(method);
            if (names != null) {
                for (int i = 0; i < names.size() && i < args.length; i++) {
                    ctx.set(names.get(i), args[i]);
                }
            }
            ctx.set("obj", obj);
            ctx.set("args", args);
            ctx.set("re", re);
            ctx.set("return", re);
            ctx.set("req", Mvcs.getReq());
            ctx.set("resp", Mvcs.getResp());
            for (String key : seg.keys()) {
                ctx.set(key, els.get(key).eval(ctx));
            }
            slogMsg = seg.render(ctx).toString();
        } else {
            slogMsg = seg.getOrginalString();
            String _slog_msg = Strings.sNull(Mvcs.getReq().getAttribute("_slog_msg"));
            if (Strings.isNotBlank(_slog_msg)) {
                slogMsg += _slog_msg;
            }
        }
        String dbParam = null;
        String dbResult = null;
        if (param && args != null) {
            try {
                dbParam = Json.toJson(args);
            } catch (Exception e1) {
                dbParam = "SLog传参:Json序列化错误";
            }
        }
        if (result && re != null) {
            try {
                dbResult = Json.toJson(re);
            } catch (Exception e1) {
                dbResult = "SLog结果:Json序列化错误";
            }
        }
        String ex = null;
        if (e != null) {
            ex = e.getMessage();
        }
        saveLog(type, tag, source, slogMsg, dbParam, dbResult, ex, tookTime);
    }

    public void saveLog(LogType type, String tag, String src, String msg, String param, String result, String ex, long tookTime) {
        Sys_log sysLog = makeLog(type, tag, src, msg, param, result, ex, tookTime);
        asyncSave(sysLog);
    }

    @Async
    public void asyncSave(Sys_log sysLog) {
        sysLogProvider.saveLog(sysLog);
    }

    public static Sys_log makeLog(LogType type, String tag, String source, String msg, String param, String result, String ex, long tookTime) {
        Sys_log sysLog = new Sys_log();
        if (type == null || tag == null) {
            throw new RuntimeException("type/tag can't null");
        }
        if (source == null) {
            StackTraceElement[] tmp = Thread.currentThread().getStackTrace();
            if (tmp.length > STATCK_TRACE_EL_NUMBER) {
                source = tmp[2].getClassName() + "#" + tmp[2].getMethodName();
            } else {
                source = "main";
            }

        }
        sysLog.setType(type.value());
        sysLog.setTag(tag);
        sysLog.setMethod(source);
        sysLog.setMsg(msg);
        sysLog.setParams(param);
        sysLog.setResult(result);
        try {
            HttpServletRequest request = Mvcs.getReq();
            if (request != null) {
                sysLog.setIp(Lang.getIP(request));
                sysLog.setUrl(request.getRequestURI());
                sysLog.setBrowser(request.getHeader("User-Agent"));
                sysLog.setOs(UserAgentUtil.parse(request.getHeader("User-Agent")).getOs().getName());
            }
            sysLog.setException(ex);
            sysLog.setExecuteTime(tookTime);
            String userId = SecurityUtil.getUserId();
            sysLog.setUserId(userId);
            sysLog.setAppId(SecurityUtil.getAppId());
            sysLog.setLoginname(SecurityUtil.getUserLoginname());
            sysLog.setUsername(SecurityUtil.getUserUsername());
            sysLog.setCreatedBy(userId);
            sysLog.setCreatedAt(System.currentTimeMillis());
            sysLog.setUpdatedBy(userId);
            sysLog.setUpdatedAt(System.currentTimeMillis());
            sysLog.setDelFlag(false);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return sysLog;
    }
}
