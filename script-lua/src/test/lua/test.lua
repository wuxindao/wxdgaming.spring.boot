---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by admin.
--- DateTime: 2024/8/21 17:31
---

function f1()
    local i1 = 1;
    print(type(i1) + i1)
end

function f2()
    f1()
end

function table.getKey(t, value)
    for k, v in pairs(t) do
        if v == value then
            return k
        end
    end
end

local tab = { a = 1, b = 2 }
print(table.getKey(tab,3))

f2()