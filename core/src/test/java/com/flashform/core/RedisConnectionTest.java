package com.flashform.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class RedisConnectionTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    public void testRedisConnection() {
        System.out.println("====== 開始測試 Redis 連線 ======");

        // 1. 定義 Key-Value
        String key = "flashform:test:ping";
        String value = "pong";

        // 2. 寫入 (Set)
        redisTemplate.opsForValue().set(key, value);
        System.out.println("寫入成功: " + key + " = " + value);

        // 3. 讀取 (Get)
        String fetchedValue = redisTemplate.opsForValue().get(key);
        System.out.println("讀取成功: " + fetchedValue);

        // 4. 驗證
        assertEquals(value, fetchedValue);

        // 5. 清理 (Delete)
        redisTemplate.delete(key);
        System.out.println("====== 測試結束，Redis 連線正常 ======");
    }
}