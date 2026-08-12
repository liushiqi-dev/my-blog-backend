package com.liushiqi.blogmain.service.impl;

import com.liushiqi.blogmain.common.exception.BusinessException;
import com.liushiqi.blogmain.common.result.PageResult;
import com.liushiqi.blogmain.dto.request.PostRequest;
import com.liushiqi.blogmain.mapper.PostMapper;
import com.liushiqi.blogmain.service.PostService;
import com.liushiqi.blogmain.vo.PostVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 文章业务逻辑实现
 */
@Slf4j
@Service
public class PostServiceImpl implements PostService {

    @Autowired
    private PostMapper postMapper;

    //todo:只能直接发布文章，不能存为草稿
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostVo publishPost(PostRequest req) {
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
        return postMapper.findById(req.getId());
    }

    //todo:待实现更新status
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
        return postMapper.findById(req.getId());
    }

    @Override
    public PostVo getPostDetail(Long id) {
        PostVo postVo = postMapper.findById(id);
        if(postVo==null||!postVo.getStatus().equals("PUBLISHED")){
            throw new BusinessException("文章不存在");
        }
        return postVo;
    }

    @Override
    public PostVo getPostDetailAll(Long id) {
        PostVo postVo = postMapper.findById(id);
        if(postVo==null){
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
        return new PageResult<>(
                postMapper.findPage(offset,size,status),
                postMapper.getTotal(status),page,size);
    }
}