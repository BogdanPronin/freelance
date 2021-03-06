package com.github.bogdan.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.github.bogdan.deserializer.*;
import com.github.bogdan.model.*;
import com.github.bogdan.serializer.*;
import com.j256.ormlite.dao.Dao;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;

import static com.github.bogdan.service.AreaOfActivityService.checkDoesSuchAreaOfActivityExist;
import static com.github.bogdan.service.AuthService.checkAuthorization;
import static com.github.bogdan.service.CtxService.*;
import static com.github.bogdan.service.PaginationService.getPagination;
import static com.github.bogdan.service.PostApplicationService.checkDoesSuchApplicationExist;
import static com.github.bogdan.service.PostApplicationService.checkIsItUsersApplication;
import static com.github.bogdan.service.PostService.getPostUser;
import static com.github.bogdan.service.SortingService.sortByQueryParams;
import static com.github.bogdan.service.UserService.*;

public class MainController {
    static Logger LOGGER = LoggerFactory.getLogger(MainController.class);

    public static <T> void add(Context ctx, Dao<T,Integer> dao,Class<T> clazz) throws JsonProcessingException, SQLException {
        ctx.header("content-type:app/json");
        SimpleModule simpleModule = new SimpleModule();
        ObjectMapper objectMapper = new ObjectMapper();

        if (clazz == User.class) {
            simpleModule.addDeserializer(User.class, new DeserializerForAddUser());
        }else {
            checkDoesBasicAuthEmpty(ctx);
            checkAuthorization(ctx);
        }

        if(clazz == Post.class){
            simpleModule.addDeserializer(Post.class, new DeserializerForAddPost(getUser(ctx.basicAuthCredentials().getUsername())));
        }else if(clazz == AreaOfActivity.class){
            checkIsUserAdmin(ctx);
            simpleModule.addDeserializer(AreaOfActivity.class,new DeserializerForAreaOfActivity());
        }else if(clazz == PostApplication.class){
            simpleModule.addDeserializer(PostApplication.class, new DeserializerForAddPostApplication(getUser(ctx.basicAuthCredentials().getUsername()).getId()));
        }else if(clazz == UserArea.class){
            simpleModule.addDeserializer(UserArea.class,new DeserializerForAddUserArea(getUser(ctx.basicAuthCredentials().getUsername()).getId()));
        }else if(clazz == Deal.class){
            simpleModule.addDeserializer(Deal.class, new DeserializerForAddDeal(getUser(ctx.basicAuthCredentials().getUsername())));
        }

        checkBodyRequestIsEmpty(ctx);
        String body = ctx.body();
        objectMapper.registerModule(simpleModule);
        Object obj = objectMapper.readValue(body, clazz);
        dao.create((T) obj);
        created(ctx);
    }

    public static <T> void get(Context ctx, Dao<T,Integer> dao,Class<T> clazz) throws JsonProcessingException, SQLException, NoSuchFieldException, IllegalAccessException {
        checkDoesBasicAuthEmpty(ctx);
        checkAuthorization(ctx);

        ctx.header("content-type:app/json");
        SimpleModule simpleModule = new SimpleModule();
        ObjectMapper objectMapper = new ObjectMapper();

        simpleModule.addSerializer(User.class, new UserGetSerializer());
        simpleModule.addSerializer(Post.class, new PostGetSerializer());
        simpleModule.addSerializer(AreaOfActivity.class, new AreaOfActivityGetSerializer());
        simpleModule.addSerializer(PostApplication.class, new PostApplicationGetSerializer());

        objectMapper.registerModule(simpleModule);
        int page = getPage(ctx);
        int size = getPagesSize(ctx);
        ArrayList<String> params = new ArrayList<>();
        if(clazz == User.class){
            User u = new User();
            params.addAll(u.getQueryParams());
            simpleModule.addSerializer(UserArea.class,new UserAreaSerializerForUser());
        }else if(clazz == Post.class){
            Post p = new Post();
            params.addAll(p.getQueryParams());
        }else if(clazz == PostApplication.class){
            PostApplication p = new PostApplication();
            params.addAll(p.getQueryParams());
        }

        String serialized;
        if(doesQueryParamsEmpty(ctx,params)){
            serialized = objectMapper.writeValueAsString(getPagination(dao,page,size));
        }else serialized = objectMapper.writeValueAsString(sortByQueryParams(dao,clazz,params,ctx));
        ctx.result(serialized);
    }

    public static <T> void change(Context ctx, Dao<T,Integer> dao,Class<T> clazz) throws JsonProcessingException, SQLException {
        checkDoesBasicAuthEmpty(ctx);

        ctx.header("content-type:app/json");
        SimpleModule simpleModule = new SimpleModule();
        ObjectMapper objectMapper = new ObjectMapper();
        int id = Integer.parseInt(ctx.pathParam("id"));
        checkBodyRequestIsEmpty(ctx);
        String body = ctx.body();

        checkAuthorization(ctx);
        if (clazz == User.class) {
            if(getUser(ctx.basicAuthCredentials().getUsername()).getRole()!= Role.ADMIN){
                if(id != getUser(ctx.basicAuthCredentials().getUsername()).getId()){
                    youAreNotAdmin(ctx);
                }
            }
            checkDoesSuchUserExist(id);
            simpleModule.addDeserializer(User.class, new DeserializerForChangeUser(id));
        }else if(clazz == AreaOfActivity.class){
            simpleModule.addDeserializer(AreaOfActivity.class, new DeserializerForAreaOfActivity(id));
            checkDoesSuchAreaOfActivityExist(id);
        } if(clazz == Post.class){
            simpleModule.addDeserializer(Post.class,new DeserializerForChangePost(id,getUser(ctx.basicAuthCredentials().getUsername()).getId()));
        }else if(clazz == PostApplication.class){
            simpleModule.addDeserializer(PostApplication.class,new DeserializerForChangePostApplication(id));
            if(getUser(ctx.basicAuthCredentials().getUsername()).getRole()!= Role.ADMIN){
                checkIsItUsersApplication(id,getUser(ctx.basicAuthCredentials().getUsername()).getId());
            }
        }else if(clazz == UserArea.class){
            simpleModule.addDeserializer(UserArea.class,new DeserializerForChangeUserArea(id,getUser(ctx.basicAuthCredentials().getUsername()).getId()));
        }else if(clazz == Deal.class){
            simpleModule.addDeserializer(Deal.class,new DeserializerForChangeDealStatus(id,getUser(ctx.basicAuthCredentials().getUsername())));
        }

        objectMapper.registerModule(simpleModule);
        Object obj = objectMapper.readValue(body, clazz);

        dao.update((T) obj);
        updated(ctx);
    }

    public static <T> void delete(Context ctx, Dao<T,Integer> dao,Class<T> clazz) throws JsonProcessingException, SQLException {
        checkDoesBasicAuthEmpty(ctx);
        ctx.header("content-type:app/json");
        SimpleModule simpleModule = new SimpleModule();
        ObjectMapper objectMapper = new ObjectMapper();
        int id = Integer.parseInt(ctx.pathParam("id"));
        checkAuthorization(ctx);
        if (clazz == User.class) {
            if(getUser(ctx.basicAuthCredentials().getUsername()).getRole()!= Role.ADMIN){
                if(id != getUser(ctx.basicAuthCredentials().getUsername()).getId()){
                    youAreNotAdmin(ctx);
                }
            }
            checkDoesSuchUserExist(id);
        }else if(clazz == Post.class){
            if(getUser(ctx.basicAuthCredentials().getUsername()).getRole()!= Role.ADMIN){
                if(id != getPostUser(id).getId()){
                    youAreNotAdmin(ctx);
                }
            }
        }else if(clazz == AreaOfActivity.class){
            checkIsUserAdmin(getUser(ctx.basicAuthCredentials().getUsername()));
            checkDoesSuchAreaOfActivityExist(id);
        }else if(clazz == PostApplication.class){
            if(getUser(ctx.basicAuthCredentials().getUsername()).getRole()!= Role.ADMIN){
                checkDoesSuchApplicationExist(id);
                checkIsItUsersApplication(id,getUser(ctx.basicAuthCredentials().getUsername()).getId());
            }
        }

        objectMapper.registerModule(simpleModule);
        Object obj = dao.queryForId(id);
        dao.delete((T) obj);
        deleted(ctx);
    }
}
