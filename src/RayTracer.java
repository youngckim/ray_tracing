import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

class Vec3 {
    double x, y, z;

    Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    Vec3 add(Vec3 v) {
        return new Vec3(x + v.x, y + v.y, z + v.z);
    }

    Vec3 sub(Vec3 v) {
        return new Vec3(x - v.x, y - v.y, z - v.z);
    }

    Vec3 mul(double t) {
        return new Vec3(x * t, y * t, z * t);
    }

    Vec3 normalize() {
        double len = Math.sqrt(x * x + y * y + z * z);
        return new Vec3(x / len, y / len, z / len);
    }

    double dot(Vec3 v) {
        return x * v.x + y * v.y + z * v.z;
    }

    Vec3 reflect(Vec3 normal) {
        return this.sub(normal.mul(2 * this.dot(normal)));
    }
}

class Ray {
    Vec3 origin;
    Vec3 direction;

    Ray(Vec3 origin, Vec3 direction) {
        this.origin = origin;
        this.direction = direction.normalize();
    }

    Vec3 pointAt(double t) {
        return origin.add(direction.mul(t));
    }
}

class Hit {
    boolean didHit;
    double distance;
    Vec3 point;
    Vec3 normal;
    Material material;

    Hit() {
        this.didHit = false;
        this.distance = Double.POSITIVE_INFINITY;
    }

    Hit(double distance, Vec3 point, Vec3 normal, Material material) {
        this.didHit = true;
        this.distance = distance;
        this.point = point;
        this.normal = normal;
        this.material = material;
    }
}

class Material {
    Vec3 color;
    double metallic;
    double roughness;

    Material(Vec3 color, double metallic, double roughness) {
        this.color = color;
        this.metallic = metallic;
        this.roughness = roughness;
    }
}

class Sphere {
    Vec3 center;
    double radius;
    Material material;

    Sphere(Vec3 center, double radius, Material material) {
        this.center = center;
        this.radius = radius;
        this.material = material;
    }

    Hit intersect(Ray ray) {
        Vec3 oc = ray.origin.sub(center);
        double a = ray.direction.dot(ray.direction);
        double b = 2.0 * oc.dot(ray.direction);
        double c = oc.dot(oc) - radius * radius;
        double discriminant = b * b - 4 * a * c;

        if (discriminant < 0) {
            return new Hit();
        }

        double t = (-b - Math.sqrt(discriminant)) / (2.0 * a);
        if (t < 0) {
            t = (-b + Math.sqrt(discriminant)) / (2.0 * a);
            if (t < 0) return new Hit();
        }

        Vec3 point = ray.pointAt(t);
        Vec3 normal = point.sub(center).normalize();
        return new Hit(t, point, normal, material);
    }
}

class Plane {
    Vec3 point;
    Vec3 normal;
    Material material;

    Plane(Vec3 point, Vec3 normal, Material material) {
        this.point = point;
        this.normal = normal.normalize();
        this.material = material;
    }

    Hit intersect(Ray ray) {
        double denom = normal.dot(ray.direction);
        if (Math.abs(denom) > 1e-6) {
            Vec3 p0l0 = point.sub(ray.origin);
            double t = p0l0.dot(normal) / denom;
            if (t >= 0) {
                return new Hit(t, ray.pointAt(t), normal, material);
            }
        }
        return new Hit();
    }
}

class Light {
    Vec3 position;
    Vec3 color;
    double intensity;

    Light(Vec3 position, Vec3 color, double intensity) {
        this.position = position;
        this.color = color;
        this.intensity = intensity;
    }
}

class RenderPanel extends JPanel {
    static final int WIDTH = 800;
    static final int HEIGHT = 600;
    static final int MAX_DEPTH = 5;
    static final double FOV = 90.0;
    
    private BufferedImage image;
    private Sphere[] spheres;
    private Plane plane;
    private Light light;
    private Vec3 camera;
    private double time = 0;

    public RenderPanel() {
        image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        
        // Setup scene with 112 spheres (2 large + 10 medium + 100 small)
        spheres = new Sphere[112];
        
        // Original two large spheres
        spheres[0] = new Sphere(new Vec3(-1.0, 0.0, -3.0), 1.0, 
                               new Material(new Vec3(0.8, 0.8, 0.8), 0.9, 0.1));
        spheres[1] = new Sphere(new Vec3(1.0, 0.0, -3.0), 1.0, 
                               new Material(new Vec3(0.8, 0.6, 0.2), 0.7, 0.3));

        // Add 10 medium spheres (1/4 size) in a circular pattern
        double mediumRadius = 0.25; // 1/4 size
        int mediumSphereCount = 10;
        double mediumCircleRadius = 2.5;
        
        for (int i = 0; i < mediumSphereCount; i++) {
            double angle = (2 * Math.PI * i) / mediumSphereCount;
            double x = mediumCircleRadius * Math.cos(angle);
            double z = -3.0 + mediumCircleRadius * Math.sin(angle);
            
            double metallic = 0.3 + (0.6 * i / mediumSphereCount);
            double roughness = 0.1 + (0.4 * i / mediumSphereCount);
            
            Vec3 color = hueToRGB((double) i / mediumSphereCount);
            
            spheres[i + 2] = new Sphere(
                new Vec3(x, 0.0, z),
                mediumRadius,
                new Material(color, metallic, roughness)
            );
        }

        // Add 100 small spheres (1/5 size) in multiple circular layers
        double smallRadius = 0.2; // 1/5 size
        int smallSphereCount = 100;
        int layerCount = 4; // Number of circular layers
        int spheresPerLayer = smallSphereCount / layerCount;
        
        for (int layer = 0; layer < layerCount; layer++) {
            double layerRadius = 3.5 + layer * 0.8; // Increasing radius for each layer
            double layerHeight = -0.5 + layer * 0.4; // Different height for each layer
            
            for (int i = 0; i < spheresPerLayer; i++) {
                double angle = (2 * Math.PI * i) / spheresPerLayer + (layer * Math.PI / layerCount);
                double x = layerRadius * Math.cos(angle);
                double z = -3.0 + layerRadius * Math.sin(angle);
                
                // Calculate index in the spheres array
                int sphereIndex = 12 + layer * spheresPerLayer + i;
                
                // Vary properties based on position
                double metallic = 0.3 + (0.6 * ((double)sphereIndex / smallSphereCount));
                double roughness = 0.1 + (0.3 * (Math.sin(angle) * 0.5 + 0.5));
                
                // Create color based on position
                double hue = ((double)sphereIndex / smallSphereCount + layer * 0.25) % 1.0;
                Vec3 color = hueToRGB(hue);
                
                spheres[sphereIndex] = new Sphere(
                    new Vec3(x, layerHeight, z),
                    smallRadius,
                    new Material(color, metallic, roughness)
                );
            }
        }

        // Move camera back to see all spheres
        camera = new Vec3(0, 1, 3);

        plane = new Plane(new Vec3(0, -1, 0), new Vec3(0, 1, 0),
                         new Material(new Vec3(0.5, 0.5, 0.5), 0.0, 0.2));

        light = new Light(new Vec3(5, 5, 5), new Vec3(1, 1, 1), 1.0);

        // Start animation timer
        Timer timer = new Timer(16, e -> {
            time += 0.02;
            updateScene();
            render();
            repaint();
        });
        timer.start();
    }

    private Vec3 hueToRGB(double hue) {
        double r = Math.abs((hue * 6 - 3) - 1);
        double g = 2 - Math.abs((hue * 6 - 2));
        double b = 2 - Math.abs((hue * 6 - 4));
        r = Math.max(0, Math.min(1, r));
        g = Math.max(0, Math.min(1, g));
        b = Math.max(0, Math.min(1, b));
        return new Vec3(r, g, b);
    }

    private void updateScene() {
        // Animate light position in a larger circle
        light.position = new Vec3(
            8 * Math.cos(time),
            5,
            8 * Math.sin(time)
        );

        // Animate large spheres
        spheres[0].center = new Vec3(
            -1.0 + 0.2 * Math.sin(time),
            0.2 * Math.cos(time),
            -3.0
        );

        spheres[1].center = new Vec3(
            1.0 + 0.2 * Math.cos(time),
            0.2 * Math.sin(time),
            -3.0
        );

        // Animate medium spheres
        for (int i = 2; i < 12; i++) {
            double individualTime = time + (2 * Math.PI * (i - 2)) / 10;
            double baseX = spheres[i].center.x;
            double baseZ = spheres[i].center.z;
            
            spheres[i].center = new Vec3(
                baseX,
                0.15 * Math.sin(individualTime * 2),
                baseZ
            );
        }

        // Animate small spheres with wave patterns
        for (int i = 12; i < spheres.length; i++) {
            double individualTime = time + (2 * Math.PI * (i - 12)) / 100;
            double baseX = spheres[i].center.x;
            double baseY = spheres[i].center.y;
            double baseZ = spheres[i].center.z;
            
            // Create wave-like motion
            double waveOffset = Math.sin(individualTime * 3 + baseX * 0.5) * 0.1;
            
            spheres[i].center = new Vec3(
                baseX,
                baseY + waveOffset,
                baseZ
            );
        }
    }

    private Vec3 trace(Ray ray, int depth) {
        if (depth <= 0) return new Vec3(0, 0, 0);

        Hit closestHit = new Hit();
        
        // Check sphere intersections
        for (Sphere sphere : spheres) {
            Hit hit = sphere.intersect(ray);
            if (hit.didHit && hit.distance < closestHit.distance) {
                closestHit = hit;
            }
        }

        // Check plane intersection
        Hit planeHit = plane.intersect(ray);
        if (planeHit.didHit && planeHit.distance < closestHit.distance) {
            closestHit = planeHit;
        }

        if (!closestHit.didHit) {
            return new Vec3(0.2, 0.3, 0.5); // Sky color
        }

        // Calculate lighting
        Vec3 lightDir = light.position.sub(closestHit.point).normalize();
        double diffuse = Math.max(0, closestHit.normal.dot(lightDir));
        
        Vec3 color = closestHit.material.color.mul(diffuse * light.intensity);

        // Handle reflections for metallic surfaces
        if (closestHit.material.metallic > 0) {
            Vec3 reflected = ray.direction.reflect(closestHit.normal);
            Ray reflectedRay = new Ray(closestHit.point.add(reflected.mul(0.001)), reflected);
            Vec3 reflectedColor = trace(reflectedRay, depth - 1);
            
            double reflectivity = closestHit.material.metallic * (1.0 - closestHit.material.roughness);
            color = color.mul(1 - reflectivity).add(reflectedColor.mul(reflectivity));
        }

        return color;
    }

    private void render() {
        double aspectRatio = (double)WIDTH / HEIGHT;
        double scale = Math.tan(Math.toRadians(FOV * 0.5));

        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                double rx = (2 * ((x + 0.5) / WIDTH) - 1) * scale * aspectRatio;
                double ry = (1 - 2 * ((y + 0.5) / HEIGHT)) * scale;
                
                Vec3 direction = new Vec3(rx, ry, -1).normalize();
                Ray ray = new Ray(camera, direction);
                
                Vec3 color = trace(ray, MAX_DEPTH);

                // Convert color to RGB
                int r = (int)(Math.min(color.x, 1) * 255);
                int g = (int)(Math.min(color.y, 1) * 255);
                int b = (int)(Math.min(color.z, 1) * 255);
                
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(image, 0, 0, this);
    }
}

public class RayTracer {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Real-time Ray Tracer");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(new RenderPanel());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
