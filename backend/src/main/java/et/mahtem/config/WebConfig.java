package et.mahtem.config;

import et.mahtem.util.Actor;
import et.mahtem.util.ApiException;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Lets controllers declare an {@link Actor} parameter to receive the authenticated staff member. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter p) {
                return Actor.class.equals(p.getParameterType());
            }

            @Override
            public Object resolveArgument(MethodParameter p, ModelAndViewContainer mav, NativeWebRequest req, WebDataBinderFactory f) {
                Authentication a = SecurityContextHolder.getContext().getAuthentication();
                if (a != null && a.getPrincipal() instanceof Actor actor) return actor;
                throw ApiException.unauthorized("Please sign in to continue.");
            }
        });
    }
}
