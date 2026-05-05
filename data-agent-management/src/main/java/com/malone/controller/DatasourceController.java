package com.malone.controller;

import com.malone.common.Result;
import com.malone.entity.Datasource;
import com.malone.service.DatasourceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/datasource")
public class DatasourceController {

    private final DatasourceService dataSourceService;

    public DatasourceController(DatasourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    @GetMapping
    public Result<List<Datasource>> findAll() {
        List<Datasource> list = dataSourceService.findAll();
        return Result.success(list);
    }

    @GetMapping("/{id}")
    public Result<Datasource> findById(@PathVariable Integer id) {
        Datasource dataSource = dataSourceService.findById(id);
        if (dataSource != null) {
            return Result.success(dataSource);
        } else {
            return Result.error(404, "DataSource not found");
        }
    }

    @PostMapping
    public Result<Boolean> save(@RequestBody Datasource dataSource) {
        boolean success = dataSourceService.save(dataSource);
        return success ? Result.success(true) : Result.error("Failed to save");
    }

    @PutMapping
    public Result<Boolean> update(@RequestBody Datasource dataSource) {
        boolean success = dataSourceService.update(dataSource);
        return success ? Result.success(true) : Result.error("Failed to update");
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteById(@PathVariable Integer id) {
        boolean success = dataSourceService.deleteById(id);
        return success ? Result.success(true) : Result.error("Failed to delete");
    }

    @GetMapping("/status/{status}")
    public Result<List<Datasource>> findByStatus(@PathVariable String status) {
        List<Datasource> list = dataSourceService.findByStatus(status);
        return Result.success(list);
    }

    @GetMapping("/type/{type}")
    public Result<List<Datasource>> findByType(@PathVariable String type) {
        List<Datasource> list = dataSourceService.findByType(type);
        return Result.success(list);
    }
}
