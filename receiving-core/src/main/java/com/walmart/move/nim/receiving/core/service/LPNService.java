package com.walmart.move.nim.receiving.core.service;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpHeaders;

public interface LPNService {

  CompletableFuture<Set<String>> retrieveLPN(int count, HttpHeaders headers);
}
