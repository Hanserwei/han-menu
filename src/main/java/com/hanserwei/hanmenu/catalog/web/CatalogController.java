package com.hanserwei.hanmenu.catalog.web;

import com.hanserwei.hanmenu.catalog.application.CatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/dishes")
class CatalogController {
  private final CatalogService catalog;

  CatalogController(CatalogService catalog) {
    this.catalog = catalog;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  CreatedDish create(@Valid @RequestBody CreateDish request) {
    return new CreatedDish(catalog.create(request.name(), request.price()));
  }

  @PostMapping("/{id}/publication")
  @ResponseStatus(HttpStatus.ACCEPTED)
  void publish(@PathVariable UUID id) {
    catalog.publish(id);
  }

  record CreateDish(
      @NotBlank @Size(max = 100) String name,
      @NotNull @DecimalMin("0.01") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2)
          BigDecimal price) {}

  record CreatedDish(UUID id) {}
}
