package com.car.wash.controller;

import com.car.wash.entity.MemberCard;
import com.car.wash.entity.WashOrder;
import com.car.wash.enums.WashState;
import com.car.wash.service.WashOrderService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class WashOrderController {

    private final WashOrderService service;

    public WashOrderController(WashOrderService service) {
        this.service = service;
    }

    @GetMapping
    public List<WashOrder> list(@RequestParam(required = false) String state,
                                @RequestParam(required = false) String keyword) {
        return service.list(state == null || state.isBlank() ? null : WashState.valueOf(state), keyword);
    }

    @PostMapping
    public WashOrder create(@RequestBody WashOrder form) {
        return service.save(form);
    }

    @PutMapping("/{id}")
    public WashOrder update(@PathVariable Long id, @RequestBody WashOrder form) {
        form.id = id;
        return service.save(form);
    }

    /** 会员卡结账。 */
    @PostMapping("/pay/{cardId}")
    public MemberCard pay(@PathVariable Long cardId, @RequestBody Map<String, Object> body) {
        Object amount = body.get("amount");
        return service.payByCard(cardId, amount == null ? 0 : Integer.valueOf(String.valueOf(amount)));
    }
}
