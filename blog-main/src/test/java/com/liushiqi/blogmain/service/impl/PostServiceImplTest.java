package com.liushiqi.blogmain.service.impl;


import com.liushiqi.blogmain.mapper.PostMapper;
import com.liushiqi.blogmain.vo.PostVo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * PostServiceImpl 单元测试
 * 使用 Mockito 模拟 Mapper，不连接真实数据库
 */
@ExtendWith(MockitoExtension.class)
class PostServiceImplTest {

    @Mock
    private PostMapper postMapper;

    @InjectMocks
    private PostServiceImpl postService;

    @Test
    void findById() {
        PostVo postVo = new PostVo();
        postVo.setStatus("PUBLISHED");
        when(postMapper.findById(1L)).thenReturn(postVo);
        
        PostVo result = postService.getPostDetailAll(1L);
        assertNotNull(result);
        assertEquals("PUBLISHED", result.getStatus());
    }

}