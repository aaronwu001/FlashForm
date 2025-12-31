package com.flashform.core.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class SeckillService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private DefaultRedisScript<Long> seckillScript;

    // 啟動時自動載入 Lua 腳本
    @PostConstruct
    public void init() {
        seckillScript = new DefaultRedisScript<>();
        seckillScript.setLocation(new ClassPathResource("scripts/seckill.lua"));
        seckillScript.setResultType(Long.class);
    }

    /**
     * 執行秒殺
     * @param productId 商品ID
     * @param userId 用戶ID
     * @return 1=成功, 0=沒庫存, -1=重複購買
     */
    public Long executeSeckill(String productId, String userId) {
        // 定義 Redis Keys
        String stockKey = "seckill:stock:" + productId;
        String historyKey = "seckill:user:" + productId;

        List<String> keys = Arrays.asList(stockKey, historyKey);

        // 執行 Lua Script
        return redisTemplate.execute(seckillScript, keys, userId);
    }
}