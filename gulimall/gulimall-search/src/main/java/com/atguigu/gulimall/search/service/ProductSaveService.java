package com.atguigu.gulimall.search.service;

import com.atguigu.common.to.es.ESSkuModel;

import java.io.IOException;
import java.util.List;

public interface ProductSaveService {
    boolean productStatusUp(List<ESSkuModel> esSkuModels) throws IOException;
}
