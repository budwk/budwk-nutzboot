package com.budwk.nb.sys.services;

import com.budwk.nb.sys.models.Sys_config;
import com.budwk.nb.base.service.BaseService;

import java.util.List;

/**
 * @author wizzer(wizzer.cn) on 2016/12/23.
 */
public interface SysConfigService extends BaseService<Sys_config> {
    /**
     * 查询所有数据
     * @return
     */
    List<Sys_config> getAllList();
}
