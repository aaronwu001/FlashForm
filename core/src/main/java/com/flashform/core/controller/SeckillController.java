package com.flashform.core.controller;

import com.flashform.core.service.SeckillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 1. 初始化庫存 (方便你重置測試)
    // 網址: http://localhost:8080/api/seckill/reset/101/5
    @GetMapping("/reset/{productId}/{stockCount}")
    public String resetStock(@PathVariable String productId, @PathVariable int stockCount) {
        redisTemplate.opsForValue().set("seckill:stock:" + productId, String.valueOf(stockCount));
        redisTemplate.delete("seckill:user:" + productId); // 清空購買名單
        return "✅ 重置成功！商品 " + productId + " 庫存已設為 " + stockCount;
    }

    // 2. 執行秒殺 (模擬搶購)
    // 網址: http://localhost:8080/api/seckill/do/101/UserA
    @GetMapping("/do/{productId}/{userId}")
    public String doSeckill(@PathVariable String productId, @PathVariable String userId) {
        Long result = seckillService.executeSeckill(productId, userId);

        if (result == 1) {
            return "🎉 恭喜 " + userId + "！搶購成功！";
        } else if (result == -1) {
            return "⛔ " + userId + " 你已經買過了，請勿貪心！";
        } else if (result == 0) {
            return "😭 抱歉，商品已售完 (庫存不足)";
        } else {
            return "⚠️ 系統異常";
        }
    }

    // 3. 檢查庫存
    @GetMapping("/check/{productId}")
    public String checkStock(@PathVariable String productId) {
        String stock = redisTemplate.opsForValue().get("seckill:stock:" + productId);
        return "📦 商品 " + productId + " 目前剩餘庫存: " + (stock != null ? stock : "0");
    }
}