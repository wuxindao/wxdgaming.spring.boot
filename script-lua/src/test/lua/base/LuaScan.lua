--- lua 扫描器
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by 無心道(15388152619).
--- DateTime: 2024/12/26 14:31
LuaScan = {}

local this = {}
--- 一级函数缓存
local oneFunCache = {}
--- 二级函数缓存
local twoFunCache = {}

function forTable0()
    local tables = this.GetTable()
    for fileName, v in pairs(tables) do
        print("所包含的文件", fileName)
    end
    print("---------------一级事件-----------------")
    local onLoginTable = LuaScan.findTopFunc("onLogin")
    for i, v in pairs(onLoginTable) do
        print(i, this.toString(v))
    end
    print("---------------二级事件-----------------")
    local onLoginTable = LuaScan.findTwoFunc("onLogin")
    for i, v in pairs(onLoginTable) do
        print(i, this.toString(v))
    end
end

--- 触发二级函数执行
function LuaScan.triggerTwoFunc(name, ...)
    local funList = LuaScan.findTwoFunc(name)
    for i, v in pairs(funList) do
        LuaScan.print("触发二级函数：", v.methodName)
        local s, e = xpcall(v.fun, debug.traceback, ...)
        gameDebug.assertPrint(s, "调用二级函数异常：", v.methodName, "【", ..., "】\n", e)
    end
end

--- 查找 全局table 中的指定函 二级函数，是 全局table 虚拟类 下面注册的函数
--- @param name string 需要查找的函数名
function LuaScan.findTwoFunc(name)
    local result = twoFunCache[name]
    if result == nil then
        result = {}
        twoFunCache[name] = result
        local tables = this.GetTable()
        for fileName, fileTable in pairs(tables) do
            for methodName, v in pairs(fileTable) do
                if type(v) == "function" and methodName == name then
                    local infoMapping = {
                        ["fileName"]   = this.get_function_file(v),
                        ["methodName"] = fileName .. "." .. methodName,
                        ["line"]       = this.getFunctionLineNumber(v),
                        ["fun"]        = v,
                    }
                    infoMapping["info"] = this.toString(infoMapping)
                    table.insert(result, infoMapping);
                end
            end
        end
    end
    return result
end

--- 触发一级函数执行
function LuaScan.triggerTopFunc(name, ...)
    local funList = LuaScan.findTopFunc(name)
    for i, v in pairs(funList) do
        LuaScan.print("触发二级函数：", v.methodName)
        local s, e = xpcall(v.fun, debug.traceback, ...)
        gameDebug.assertPrint(s, "调用一级函数异常：", v.methodName, "【", ..., "】\n", e)
    end
end

--- 查找一级函数 就是全局函数
function LuaScan.findTopFunc(name)
    local result = oneFunCache[name]
    if result == nil then
        result = {}
        oneFunCache[name] = result
        for methodName, v in pairs(_G) do
            if type(v) == "function" and methodName == name then
                table.insert(
                        result,
                        {
                            ["fileName"]   = this.get_function_file(v),
                            ["methodName"] = "_G." .. methodName,
                            ["line"]       = this.getFunctionLineNumber(v),
                            ["fun"]        = v,
                        }
                );
            end
        end
    end
    return result
end

function this.toString(funMapping)
    return funMapping.methodName .. "() 文件：" .. funMapping.fileName .. " line:" .. funMapping.line;
end

--- 获取当前虚拟机中所有的表
function this.GetTable()
    local result = {}
    for k, v in pairs(_G) do
        if type(v) == "table" then
            if k ~= "__index"
                    and k ~= "_G"
                    and k ~= "os"
                    and k ~= "io"
                    and k ~= "string"
                    and k ~= "utf8"
                    and k ~= "java"
                    and k ~= "math"
                    and k ~= "package"
                    and k ~= "debug"
                    and k ~= "coroutine"
            then
                result[k] = v
            end
        end
    end
    return result;
end

---获取函数所在的文件名
function this.get_function_file(func)
    local info = debug.getinfo(func)
    return info.short_src or "匿名文件"
end

---获取函数所在文件行号
function this.getFunctionLineNumber(func)
    local debugInfo = debug.getinfo(func)
    --获取不到函数名字，只能又文件名和行数表达了
    return debugInfo.linedefined or -1
end

local offPrint = false
function LuaScan.print(...)
    if offPrint then
        return
    end
    local varString = gameDebug.toStrings(" ", ...)
    print(varString)
end