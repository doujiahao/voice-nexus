package org.jeecg.modules.call.controller;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.call.entity.Customer;
import org.jeecg.modules.call.entity.CustomerContact;
import org.jeecg.modules.call.mapper.CustomerContactMapper;
import org.jeecg.modules.call.mapper.CustomerMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@Slf4j
@Tag(name = "客户档案管理")
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    @Autowired
    private CustomerMapper customerMapper;
    @Autowired
    private CustomerContactMapper customerContactMapper;

    @Operation(summary = "客户列表")
    @GetMapping
    public Result<Page<Customer>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {

        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Customer::getName, keyword)
                    .or().like(Customer::getCustomerNo, keyword)
                    .or().like(Customer::getAddress, keyword));
        }
        wrapper.orderByDesc(Customer::getCreateTime);

        Page<Customer> result = customerMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return Result.OK(result);
    }

    @Operation(summary = "客户详情")
    @GetMapping("/{id}")
    public Result<JSONObject> detail(@PathVariable String id) {
        Customer customer = customerMapper.selectById(id);
        if (customer == null) {
            return Result.error("客户不存在");
        }

        List<CustomerContact> contacts = customerContactMapper.selectList(
                new LambdaQueryWrapper<CustomerContact>().eq(CustomerContact::getCustomerId, id));

        JSONObject data = JSONObject.parseObject(JSONObject.toJSONString(customer));
        data.put("contacts", contacts);
        return Result.OK(data);
    }

    @Operation(summary = "新增客户")
    @PostMapping
    public Result<Customer> create(@RequestBody Customer customer) {
        customer.setCreateTime(new Date());
        customerMapper.insert(customer);
        return Result.OK(customer);
    }

    @Operation(summary = "更新客户")
    @PutMapping("/{id}")
    public Result<Customer> update(@PathVariable String id, @RequestBody Customer customer) {
        customer.setId(id);
        customer.setUpdateTime(new Date());
        customerMapper.updateById(customer);
        return Result.OK(customer);
    }

    @Operation(summary = "删除客户")
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        customerMapper.deleteById(id);
        return Result.OK("删除成功");
    }

    @Operation(summary = "客户联系方式列表")
    @GetMapping("/{customerId}/contacts")
    public Result<List<CustomerContact>> listContacts(@PathVariable String customerId) {
        List<CustomerContact> contacts = customerContactMapper.selectList(
                new LambdaQueryWrapper<CustomerContact>().eq(CustomerContact::getCustomerId, customerId));
        return Result.OK(contacts);
    }

    @Operation(summary = "新增联系方式")
    @PostMapping("/{customerId}/contacts")
    public Result<CustomerContact> addContact(@PathVariable String customerId, @RequestBody CustomerContact contact) {
        contact.setCustomerId(customerId);
        contact.setCreateTime(new Date());
        customerContactMapper.insert(contact);
        return Result.OK(contact);
    }

    @Operation(summary = "更新联系方式")
    @PutMapping("/{customerId}/contacts/{contactId}")
    public Result<CustomerContact> updateContact(@PathVariable String customerId,
                                                  @PathVariable String contactId,
                                                  @RequestBody CustomerContact contact) {
        contact.setId(contactId);
        contact.setCustomerId(customerId);
        contact.setUpdateTime(new Date());
        customerContactMapper.updateById(contact);
        return Result.OK(contact);
    }

    @Operation(summary = "删除联系方式")
    @DeleteMapping("/{customerId}/contacts/{contactId}")
    public Result<?> deleteContact(@PathVariable String customerId, @PathVariable String contactId) {
        customerContactMapper.deleteById(contactId);
        return Result.OK("删除成功");
    }

    @Operation(summary = "按手机号查客户")
    @GetMapping("/by-phone")
    public Result<Customer> findByPhone(@RequestParam String phone) {
        CustomerContact contact = customerContactMapper.selectOne(
                new LambdaQueryWrapper<CustomerContact>()
                        .eq(CustomerContact::getContactType, "PHONE")
                        .eq(CustomerContact::getContactValue, phone)
                        .last("LIMIT 1"));
        if (contact == null) {
            return Result.OK(null);
        }
        Customer customer = customerMapper.selectById(contact.getCustomerId());
        return Result.OK(customer);
    }
}
