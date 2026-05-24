package com.example.controller;

import com.example.entity.Movie;
import com.example.repository.MovieRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    private final MovieRepository repo;

    public MovieController(MovieRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Movie> getAll() {
        return repo.findAll();
    }

    @PostMapping
    public Movie add(@RequestBody Movie movie) {
        return repo.save(movie);
    }
    @PutMapping("/{id}")
    public Movie update(@PathVariable Integer id, @RequestBody Movie details) {
        return repo.findById(id).map(m -> {
            m.setTitle(details.getTitle());
            m.setDescription(details.getDescription());
            m.setDuration(details.getDuration());
            m.setTrailerUrl(details.getTrailerUrl());
            m.setGenre(details.getGenre());
            m.setDirector(details.getDirector());
            m.setStatus(details.getStatus());
            m.setPosterUrl(details.getPosterUrl());
            return repo.save(m);
        }).orElseThrow(() -> new RuntimeException("Không tìm thấy!"));
    }
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
        repo.deleteById(id);
    }
}