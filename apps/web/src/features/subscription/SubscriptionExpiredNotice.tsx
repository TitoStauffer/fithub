import { Link } from 'react-router';

/** O aviso de renovação, com o caminho para a tela de assinatura (DEV-0102). */
export function SubscriptionExpiredNotice() {
    return (
        <p role="alert">
            Sua assinatura venceu: criar, editar e publicar fica bloqueado até você assinar. O que já foi
            entregue continua com o cliente.{' '}
            <Link className="underline" to="/app/assinatura">
                Renovar a assinatura
            </Link>
        </p>
    );
}
