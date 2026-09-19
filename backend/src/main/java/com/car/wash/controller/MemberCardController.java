package com.car.wash.controller;

import com.car.wash.entity.MemberCard;
import com.car.wash.enums.CardState;
import com.car.wash.service.MemberCardService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
public class MemberCardController {

    private final MemberCardService service;

    public MemberCardController(MemberCardService service) {
        this.service = service;
    }

    @GetMapping
    public List<MemberCard> list(@RequestParam(required = false) String state,
                                 @RequestParam(required = false) String keyword) {
        return service.list(state == null || state.isBlank() ? null : CardState.valueOf(state), keyword);
    }

    @PostMapping
    public MemberCard create(@RequestBody MemberCard form) {
        return service.save(form);
    }

    @PutMapping("/{id}")
    public MemberCard update(@PathVariable Long id, @RequestBody MemberCard form) {
        form.id = id;
        return service.save(form);
    }
}
