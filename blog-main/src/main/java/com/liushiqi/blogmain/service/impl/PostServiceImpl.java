package com.liushiqi.blogmain.service.impl;

import com.liushiqi.blogmain.common.exception.BusinessException;
import com.liushiqi.blogmain.common.result.PageResult;
import com.liushiqi.blogmain.common.util.RedisUtils;
import com.liushiqi.blogmain.dto.request.PostRequest;
import com.liushiqi.blogmain.mapper.PostMapper;
import com.liushiqi.blogmain.service.PostService;
import com.liushiqi.blogmain.vo.PostVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 文章业务逻辑实现
 */
@Slf4j
@Service
public class PostServiceImpl implements PostService {

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisUtils redisUtils;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostVo createPost(PostRequest req) {
        if (req.getSummary()==null){
            req.setSummary(req.getContent().substring(0, Math.min(100, req.getContent().length())));
        }
        Long authorId = (Long) Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();

        Integer categoryCount= postMapper.countCategoriesByIds(req.getCategoryIds());
        if(categoryCount!=req.getCategoryIds().size()){
            throw new BusinessException("分类不存在");
        }
        postMapper.insert(authorId,req);
        postMapper.insertCategories(req.getId(),req.getCategoryIds());
//        redisTemplate.delete(redisTemplate.keys("posts:*"));//O(N)查找所有Key会阻塞Redis
        redisUtils.deleteByPattern("posts:*");//采用分批扫描，避免阻塞Redis
        return postMapper.findById(req.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostVo updatePost(PostRequest req) {
        Integer categoryCount= postMapper.countCategoriesByIds(req.getCategoryIds());
        if(categoryCount!=req.getCategoryIds().size()){
            throw new BusinessException("分类不存在");
        }
        postMapper.update(req);
        postMapper.deleteCategoriesByPostId(req.getId());
        postMapper.insertCategories(req.getId(),req.getCategoryIds());
        redisUtils.deleteByPattern("posts:*");
        return postMapper.findById(req.getId());
    }

    @Override
    public PostVo getPostDetail(Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> Objects.equals(a.getAuthority(), "ROLE_ADMIN"));

        PostVo postVo = postMapper.findById(id);
        if(postVo==null||(!isAdmin&&!postVo.getStatus().equals("PUBLISHED"))){
            throw new BusinessException("文章不存在");
        }
        return postVo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long id) {
        int rows = postMapper.deleteById(id);
        if(rows==0){
            throw new BusinessException("文章不存在");
        }
        postMapper.deleteCategoriesByPostId(id);
        redisUtils.deleteByPattern("posts:*");
    }

    @Override
    public PageResult listPosts(Integer page, Integer size, String status){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> Objects.equals(a.getAuthority(), "ROLE_ADMIN"));
        int offset=(page-1)*size;
        if(size>50){
            size=50;
        }
        if(!isAdmin){
            status="PUBLISHED";
        }
        String key="posts:"+status+":"+page+":"+size;
        Object cache = redisTemplate.opsForValue().get(key);
        if(cache!=null){
            return (PageResult) cache;
        }
        PageResult result = new PageResult<>(
                postMapper.findPage(offset,size,status),
                postMapper.getTotal(status),page,size);
        redisUtils.setWithRandomTtl(key, result, 30, 5, TimeUnit.MINUTES);
        return result;
    }
}