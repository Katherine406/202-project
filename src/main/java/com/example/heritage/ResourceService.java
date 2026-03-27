package com.example.heritage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class ResourceService {
    private static final int TITLE_MAX_LENGTH = 30;
    private static final int DESCRIPTION_MAX_LENGTH = 2000;
    private static final List<String> ALLOWED_FILE_EXTENSIONS = List.of(".docx", ".pdf", ".txt");
    private static final List<String> CATEGORIES = List.of("传统技艺", "民俗活动", "历史建筑", "地方戏曲", "非遗美食", "其他");
    private static final List<String> PLACES = List.of("北京", "上海", "广州", "成都", "西安");
    private static final List<String> RECOMMENDED_TAGS = List.of("非遗", "口述史", "老物件", "节庆", "工艺流程");
    private static final List<String> COPYRIGHT_OPTIONS = List.of(
            "原创授权，可用于文化遗产研究与展示",
            "仅限教育与非商业传播，需署名",
            "社区共创内容，引用需注明来源与贡献者",
            "受访者授权发布，禁止二次改编",
            "馆藏资料数字化版本，受馆方版权政策约束"
    );

    private final ResourceRepository repository;
    private final Path uploadDir;

    public ResourceService(ResourceRepository repository, @Value("${app.upload-dir}") String uploadDir) throws IOException {
        this.repository = repository;
        this.uploadDir = Paths.get(uploadDir);
        Files.createDirectories(this.uploadDir);
    }

    @Transactional
    public Resource createDraft(Long contributorId) {
        Resource resource = new Resource();
        resource.setContributorId(contributorId);
        resource.setStatus(ResourceStatus.DRAFT);
        return repository.save(resource);
    }

    @Transactional
    public Resource saveDraft(Long resourceId, Long contributorId, Resource payload) {
        Resource resource = mustBeOwner(resourceId, contributorId);
        ensureEditable(resource);
        applyMetadata(resource, payload);
        resource.setStatus(ResourceStatus.DRAFT);
        resource.touch();
        return repository.save(resource);
    }

    @Transactional
    public Resource uploadFile(Long resourceId, Long contributorId, MultipartFile file) throws IOException {
        Resource resource = mustBeOwner(resourceId, contributorId);
        ensureEditable(resource);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择上传文件");
        }
        String originalName = Objects.requireNonNull(file.getOriginalFilename());
        validateFileType(originalName);
        String safeName = System.currentTimeMillis() + "_" + originalName.replace(" ", "_");
        Path target = uploadDir.resolve(safeName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        resource.setFilePath(target.toString());
        resource.touch();
        return repository.save(resource);
    }

    @Transactional
    public Resource updateExternalLink(Long resourceId, Long contributorId, String externalLink) {
        Resource resource = mustBeOwner(resourceId, contributorId);
        ensureEditable(resource);
        resource.setExternalLink(externalLink);
        resource.touch();
        return repository.save(resource);
    }

    @Transactional
    public Resource submit(Long resourceId, Long contributorId) {
        Resource resource = mustBeOwner(resourceId, contributorId);
        if (!(resource.getStatus() == ResourceStatus.DRAFT || resource.getStatus() == ResourceStatus.REJECTED)) {
            throw new IllegalStateException("当前状态不可提交审核");
        }
        if (isBlank(resource.getTitle()) || isBlank(resource.getDescription())) {
            throw new IllegalStateException("标题和描述为必填项");
        }
        if (resource.getTitle().length() > TITLE_MAX_LENGTH) {
            throw new IllegalStateException("标题不能超过30字");
        }
        if (resource.getDescription().length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalStateException("描述不能超过2000字");
        }
        if (isBlank(resource.getFilePath()) || isBlank(resource.getExternalLink())) {
            throw new IllegalStateException("提交前必须同时上传文件并填写外链");
        }
        resource.setStatus(ResourceStatus.PENDING_REVIEW);
        resource.touch();
        return repository.save(resource);
    }

    @Transactional
    public Resource review(Long resourceId, boolean approved, String feedback) {
        Resource resource = repository.findById(resourceId).orElseThrow(() -> new IllegalArgumentException("资源不存在"));
        if (resource.getStatus() != ResourceStatus.PENDING_REVIEW) {
            throw new IllegalStateException("仅待审核资源可审核");
        }
        if (isBlank(feedback)) {
            throw new IllegalStateException("审核通过或拒绝都必须填写反馈");
        }
        if (approved) {
            resource.setStatus(ResourceStatus.APPROVED);
            resource.setReviewFeedback(feedback);
        } else {
            resource.setStatus(ResourceStatus.REJECTED);
            resource.setReviewFeedback(feedback);
        }
        resource.touch();
        return repository.save(resource);
    }

    public List<Resource> listMine(Long contributorId) {
        return repository.findByContributorIdOrderByUpdatedAtDesc(contributorId);
    }

    public List<Resource> listDrafts(Long contributorId) {
        return repository.findByContributorIdAndStatusInOrderByUpdatedAtDesc(
                contributorId,
                List.of(ResourceStatus.DRAFT, ResourceStatus.REJECTED)
        );
    }

    public List<Resource> listPending() {
        return repository.findByStatusOrderByUpdatedAtDesc(ResourceStatus.PENDING_REVIEW);
    }

    public List<Resource> listApproved() {
        return repository.findByStatusOrderByUpdatedAtDesc(ResourceStatus.APPROVED);
    }

    @Transactional
    public void deleteDraft(Long resourceId, Long contributorId) {
        Resource resource = mustBeOwner(resourceId, contributorId);
        if (resource.getStatus() != ResourceStatus.DRAFT) {
            throw new IllegalStateException("仅草稿可删除");
        }
        repository.delete(resource);
    }

    public Map<String, Object> options() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categories", CATEGORIES);
        result.put("places", PLACES);
        result.put("recommendedTags", RECOMMENDED_TAGS);
        result.put("copyrightOptions", COPYRIGHT_OPTIONS);
        result.put("titleMaxLength", TITLE_MAX_LENGTH);
        result.put("descriptionMaxLength", DESCRIPTION_MAX_LENGTH);
        result.put("allowedFileExtensions", ALLOWED_FILE_EXTENSIONS);
        return result;
    }

    private Resource mustBeOwner(Long resourceId, Long contributorId) {
        Resource resource = repository.findById(resourceId).orElseThrow(() -> new IllegalArgumentException("资源不存在"));
        if (!Objects.equals(resource.getContributorId(), contributorId)) {
            throw new IllegalStateException("只能操作自己创建的资源");
        }
        return resource;
    }

    private void ensureEditable(Resource resource) {
        if (!(resource.getStatus() == ResourceStatus.DRAFT || resource.getStatus() == ResourceStatus.REJECTED)) {
            throw new IllegalStateException("仅草稿和已拒绝资源可编辑");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void applyMetadata(Resource resource, Resource payload) {
        if (payload == null) {
            return;
        }
        validateChoice(payload.getCategory(), CATEGORIES, "分类");
        validateChoice(payload.getPlace(), PLACES, "地点");
        validateChoice(payload.getCopyrightDeclaration(), COPYRIGHT_OPTIONS, "版权声明");
        if (!isBlank(payload.getTitle()) && payload.getTitle().length() > TITLE_MAX_LENGTH) {
            throw new IllegalStateException("标题不能超过30字");
        }
        if (!isBlank(payload.getDescription()) && payload.getDescription().length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalStateException("描述不能超过2000字");
        }
        resource.setTitle(payload.getTitle());
        resource.setCategory(payload.getCategory());
        resource.setTheme(null);
        resource.setPlace(payload.getPlace());
        resource.setDescription(payload.getDescription());
        resource.setTags(payload.getTags());
        resource.setCopyrightDeclaration(payload.getCopyrightDeclaration());
        resource.setExternalLink(payload.getExternalLink());
    }

    private void validateChoice(String value, List<String> options, String fieldName) {
        if (!isBlank(value) && !options.contains(value)) {
            throw new IllegalStateException(fieldName + "不在可选范围内");
        }
    }

    private void validateFileType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        boolean allowed = ALLOWED_FILE_EXTENSIONS.stream().anyMatch(lower::endsWith);
        if (!allowed) {
            throw new IllegalStateException("文件类型仅支持 docx、pdf、txt");
        }
    }
}
