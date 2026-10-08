package com.guangying.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PurchaseFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void userCanCompletePurchaseFlow() throws Exception {
        String account = "integration_user";
        String password = "Test123456";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "account": "%s",
                                  "password": "%s",
                                  "userNick": "Integration Test",
                                  "inviteCode": "lpf"
                                }
                                """.formatted(account, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account": "%s", "password": "%s"}
                                """.formatted(account, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = objectMapper.readTree(loginBody).path("data").path("token").asText();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.account").value(account))
                .andExpect(header().exists("X-Request-Id"));

        // 普通用户即使持有有效 JWT，也不能初始化热门场次或操作 Outbox 死信。
        mockMvc.perform(post("/api/queue/admin/init-hot")
                        .header("Authorization", "Bearer " + token)
                        .param("scheduleId", "1")
                        .param("maxAdmission", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/api/admin/outbox/dead")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/ajax/movieOnInfoList"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movieList").isArray());
        mockMvc.perform(get("/ajax/availableDates").param("movieId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        mockMvc.perform(get("/api/seat/layout").param("scheduleId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows").value(10));

        String orderBody = mockMvc.perform(post("/api/seat/lock")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey": "purchase-flow-1", "scheduleId": 1, "seats": [{"row": 2, "col": 3}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode order = objectMapper.readTree(orderBody).path("data");
        String orderNo = order.path("orderNo").asText();

        // 客户端因网络超时重试同一锁座请求时，应返回原待支付订单，
        // 不能重复扣库存、重复建单或释放第一次请求持有的锁。
        mockMvc.perform(post("/api/seat/lock")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey": "purchase-flow-1", "scheduleId": 1, "seats": [{"row": 2, "col": 3}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderNo").value(orderNo));

        mockMvc.perform(post("/api/seat/lock")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey": "purchase-flow-1", "scheduleId": 1, "seats": [{"row": 2, "col": 4}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409));

        mockMvc.perform(post("/api/payment/pay")
                        .header("Authorization", "Bearer " + token)
                        .param("orderNo", orderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.statusDesc").value("已支付"));

        mockMvc.perform(get("/api/order/list")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].orderNo").value(orderNo))
                .andExpect(jsonPath("$.data[0].statusDesc").value("已支付"));
    }
}
