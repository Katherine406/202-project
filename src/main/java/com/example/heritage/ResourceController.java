package com.example.heritage;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {
    private final ResourceService service;
    private final AuthService authService;

    public ResourceController(ResourceService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @PostMapping
    public Resource createDraft(@RequestHeader("X-Auth-Token") String token) {
        UserSession session = authService.requireSession(token);
        requireRole(session, UserRole.CONTRIBUTOR);
        return service.createDraft(session.userId());
    }

    @PostMapping("/{id}/save-draft")
    public Resource saveDraft(@PathVariable Long id,
                              @RequestHeader("X-Auth-Token") String token,
                              @RequestBody(required = false) Resource payload) {
        UserSession session = authService.requireSession(token);
        requireRole(session, UserRole.CONTRIBUTOR);
        return service.saveDraft(id, session.userId(), payload);
    }

    @PostMapping("/{id}/file")
    public Resource uploadFile(@PathVariable Long id,
                               @RequestHeader("X-Auth-Token") String token,
                               @RequestParam("file") MultipartFile file) throws IOException {
        UserSession session = authService.requireSession(token);
        requireRole(session, UserRole.CONTRIBUTOR);
        return service.uploadFile(id, session.userId(), file);
    }

    @PatchMapping("/{id}/external-link")
    public Resource externalLink(@PathVariable Long id,
                                 @RequestHeader("X-Auth-Token") String token,
                                 @RequestBody Map<String, String> body) {
        UserSession session = authService.requireSession(token);
        requireRole(session, UserRole.CONTRIBUTOR);
        return service.updateExternalLink(id, session.userId(), body.get("externalLink"));
    }

    @PostMapping("/{id}/submit")
    public Resource submit(@PathVariable Long id,
                           @RequestHeader("X-Auth-Token") String token) {
        UserSession session = authService.requireSession(token);
        requireRole(session, UserRole.CONTRIBUTOR);
        return service.submit(id, session.userId());
    }

    @PostMapping("/{id}/review")
    public Resource review(@PathVariable Long id,
                           @RequestHeader("X-Auth-Token") String token,
                           @RequestBody Map<String, Object> body) {
        UserSession session = authService.requireSession(token);
        requireRole(session, UserRole.ADMIN);
        boolean approved = Boolean.TRUE.equals(body.get("approved"));
        String feedback = body.get("feedback") == null ? null : body.get("feedback").toString();
        return service.review(id, approved, feedback);
    }

    @GetMapping("/mine")
    public List<Resource> mine(@RequestHeader("X-Auth-Token") String token) {
        UserSession session = authService.requireSession(token);
        requireRole(session, UserRole.CONTRIBUTOR);
        return service.listMine(session.userId());
    }

    @GetMapping("/drafts")
    public List<Resource> drafts(@RequestHeader("X-Auth-Token") String token) {
        UserSession session = authService.requireSession(token);
        requireRole(session, UserRole.CONTRIBUTOR);
        return service.listDrafts(session.userId());
    }

    @DeleteMapping("/{id}/draft")
    public Map<String, String> deleteDraft(@PathVariable Long id, @RequestHeader("X-Auth-Token") String token) {
        UserSession session = authService.requireSession(token);
        requireRole(session, UserRole.CONTRIBUTOR);
        service.deleteDraft(id, session.userId());
        return Map.of("message", "草稿已删除");
    }

    @GetMapping("/pending")
    public List<Resource> pending(@RequestHeader("X-Auth-Token") String token) {
        UserSession session = authService.requireSession(token);
        requireRole(session, UserRole.ADMIN);
        return service.listPending();
    }

    @GetMapping("/approved")
    public List<Resource> approved(@RequestHeader("X-Auth-Token") String token) {
        UserSession session = authService.requireSession(token);
        if (!(session.role() == UserRole.CONTRIBUTOR || session.role() == UserRole.VIEWER)) {
            throw new IllegalStateException("当前角色无权限");
        }
        return service.listApproved();
    }

    @GetMapping("/options")
    public Map<String, Object> options(@RequestHeader("X-Auth-Token") String token) {
        UserSession session = authService.requireSession(token);
        requireRole(session, UserRole.CONTRIBUTOR);
        return service.options();
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> badRequest(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    private void requireRole(UserSession session, UserRole expectedRole) {
        if (session.role() != expectedRole) {
            throw new IllegalStateException("当前角色无权限");
        }
    }
}
