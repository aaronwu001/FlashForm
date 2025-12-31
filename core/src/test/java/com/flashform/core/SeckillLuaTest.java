package com.flashform.core;

import com.flashform.core.service.SeckillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class SeckillLuaTest {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    public void setup() {
        // 每次測試前先初始化數據
        redisTemplate.opsForValue().set("seckill:stock:101", "5"); // 庫存設為 5
        redisTemplate.delete("seckill:user:101"); // 清空購買名單
    }

    @Test
    public void testSeckillFlow() {
        String productId = "101";

        // 1. 用戶 A 第一次購買 -> 應該成功 (1)
        Long resultA = seckillService.executeSeckill(productId, "UserA");
        System.out.println("UserA 第一次購買結果: " + resultA);
        assertEquals(1L, resultA);

        // 2. 用戶 A 重複購買 -> 應該失敗 (-1)
        Long resultA_Dup = seckillService.executeSeckill(productId, "UserA");
        System.out.println("UserA 重複購買結果: " + resultA_Dup);
        assertEquals(-1L, resultA_Dup);

        // 3. 檢查庫存 -> 應該剩 4
        String stock = redisTemplate.opsForValue().get("seckill:stock:101");
        System.out.println("當前庫存: " + stock);
        assertEquals("4", stock);
    }
}