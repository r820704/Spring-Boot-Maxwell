package com.example.demo.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("maxwell")
public class Config {


  private String host;

  private int port = 3306;

  private String user;

  private String password;

  private String database;


  private String includeDatabases;

  private  String includeTables;



}
