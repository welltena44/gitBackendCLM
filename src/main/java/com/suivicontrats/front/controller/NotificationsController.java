package com.suivicontrats.front.controller;

import com.suivicontrats.front.entity.Notifications;
import com.suivicontrats.front.service.NotificationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationsController {

    private final NotificationsService service;

    @GetMapping
    public List<Notifications> all(){ return service.all(); }

    @GetMapping("/{id}")
    public Notifications one(@PathVariable Long id){ return service.getOne(id); }

    @GetMapping("/user/{userId}")
    public List<Notifications> byUser(@PathVariable Integer userId){
        return service.byUser(userId);
    }

    @GetMapping("/user/{userId}/non-lues")
    public List<Notifications> nonLues(@PathVariable Integer userId){
        return service.nonLues(userId);
    }

    @GetMapping("/user/{userId}/count")
    public long count(@PathVariable Integer userId){
        return service.countNonLues(userId);
    }

    @PostMapping
    public Notifications create(@RequestBody Notifications n){ return service.save(n); }

    @PutMapping("/{id}")
    public Notifications update(@PathVariable Long id, @RequestBody Notifications n){
        return service.update(id, n);
    }

    @PutMapping("/{id}/lu")
    public Notifications marquerLu(@PathVariable Long id){ return service.marquerLu(id); }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id){ service.delete(id); }
}
