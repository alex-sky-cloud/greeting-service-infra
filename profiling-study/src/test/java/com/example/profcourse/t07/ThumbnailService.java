package com.example.profcourse.t07;

/** Вторая нагрузка теста 07.2: 64 КБ на картинку. */
final class ThumbnailService {

    byte[] thumbnail(int id) {
        byte[] image = new byte[64 * 1024];
        image[id % image.length] = 1;
        return image;
    }
}
