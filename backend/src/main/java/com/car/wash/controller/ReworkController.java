package com.car.wash.controller;

import com.car.wash.entity.ReworkOrder;
import com.car.wash.enums.ReworkState;
import com.car.wash.service.ReworkService;
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
@RequestMapping("/api/reworks")
public class ReworkController {

    private final ReworkService service;

    public ReworkController(ReworkService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReworkOrder> list(@RequestParam(required = false) String state,
                                  @RequestParam(required = false) String keyword) {
        return service.list(state == null || state.isBlank() ? null : ReworkState.valueOf(state), keyword);
    }

    @GetMapping("/{id}")
    public ReworkOrder get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 挂一张回炉。 */
    @PostMapping
    public ReworkOrder create(@RequestBody ReworkOrder form) {
        return service.create(form);
    }

    /** 把回炉往前推一步。 */
    @PutMapping("/{id}")
    public ReworkOrder advance(@PathVariable Long id, @RequestBody ReworkOrder form) {
        return service.advance(id, form.reworkState);
    }

    /** 改派到另一个空闲工位。 */
    @PostMapping("/{id}/reassign")
    public ReworkOrder reassign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object bayId = body.get("bayId");
        return service.reassign(id, bayId == null ? null : Long.valueOf(String.valueOf(bayId)));
    }
}
