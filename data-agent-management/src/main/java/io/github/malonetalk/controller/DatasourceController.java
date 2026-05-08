package io.github.malonetalk.controller;

import io.github.malonetalk.common.Result;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.service.DatasourceService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
