package com.agv.backend.service;

import com.agv.backend.common.BusinessException;
import com.agv.backend.dto.DictRequest;
import com.agv.backend.entity.SysDict;
import com.agv.backend.repository.SysDictRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DictService {

    private final SysDictRepository dictRepository;

    public DictService(SysDictRepository dictRepository) {
        this.dictRepository = dictRepository;
    }

    /** 前端下拉框用：只返回启用项，按 sort 升序。 */
    @Transactional(readOnly = true)
    public List<SysDict> listEnabled(String dictType) {
        return dictRepository.findByDictTypeAndStatusOrderBySortAsc(dictType, 1);
    }

    /** 管理员维护用：返回全部。 */
    @Transactional(readOnly = true)
    public List<SysDict> listAll(String dictType) {
        return dictType == null || dictType.isBlank()
                ? dictRepository.findAll()
                : dictRepository.findByDictTypeOrderBySortAsc(dictType);
    }

    @Transactional
    public SysDict create(DictRequest request) {
        if (dictRepository.existsByDictTypeAndDictCode(request.dictType(), request.dictCode())) {
            throw new BusinessException("同类型下字典编码已存在：" + request.dictCode());
        }
        SysDict dict = new SysDict();
        apply(dict, request);
        return dictRepository.save(dict);
    }

    @Transactional
    public SysDict update(Long id, DictRequest request) {
        SysDict dict = dictRepository.findById(id)
                .orElseThrow(() -> new BusinessException(4040, "字典项不存在：" + id));
        apply(dict, request);
        return dictRepository.save(dict);
    }

    @Transactional
    public void delete(Long id) {
        dictRepository.deleteById(id);
    }

    private void apply(SysDict dict, DictRequest request) {
        dict.setDictType(request.dictType());
        dict.setDictCode(request.dictCode());
        dict.setDictValue(request.dictValue());
        dict.setSort(request.sort() == null ? 0 : request.sort());
        dict.setStatus(request.status() == null ? 1 : request.status());
    }
}
