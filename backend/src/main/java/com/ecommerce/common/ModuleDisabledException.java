package com.ecommerce.common;

public class ModuleDisabledException extends BusinessException {

    private final String module;

    public ModuleDisabledException(String module, String moduleName) {
        super(Constants.ErrorCode.MODULE_DISABLED, moduleName + "模块已关闭");
        this.module = module;
    }

    public String getModule() {
        return module;
    }
}
