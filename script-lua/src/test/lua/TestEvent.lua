---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by 無心道(15388152619).
--- DateTime: 2024/12/30 14:09
---

TestEvent = {}

function TestEvent.onInit()
    print("onInit")
end

function TestEvent.onLogin(actor)
    print("onLogin", actor:toString())
    redisValue("set", "sdf:sdfw:2222", "set")
    print(redisValue("get", "sdf:sdfw:2222"))

    print(redisValue("setIfAbsent", "sdf:sdfw:2222", "set2"))

    redisValue("increment", "sdf:sdfw:1", 1)
    print(redisValue("get", "sdf:sdfw:1"))
    redisValue("increment", "sdf:sdfw:1", 1)
    print(redisValue("get", "sdf:sdfw:1"))
end

function TestEvent.onEnterMap(actor, ...)
    print("onEnterMap", actor:toString(), ...)
end