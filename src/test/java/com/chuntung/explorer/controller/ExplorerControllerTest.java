package com.chuntung.explorer.controller;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExplorerControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void upload() throws Exception {
        mockMvc.perform(multipart("/upload")
                        .file("file", "xxx".getBytes())
                        .param("name", "testname")
                        .header("Host", "baidu.localhost:2024")
                )
                .andExpect(status().isOk());
    }
}